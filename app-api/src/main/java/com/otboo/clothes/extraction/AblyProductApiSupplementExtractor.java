package com.otboo.clothes.extraction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.common.http.ExternalApiClient;
import com.otboo.common.http.ExternalApiClientFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/** 에이블리 상품 API에서 정적 대표 이미지, 상세 설명·이미지와 옵션을 보충한다. */
@Component
public class AblyProductApiSupplementExtractor implements ProductPageSupplementExtractor {

    private static final Set<String> ABLY_PRODUCT_HOSTS = Set.of(
            "mobile.a-bly.com",
            "m.a-bly.com"
    );
    private static final String ABLY_API_BASE_URL = "https://api.a-bly.com";
    private static final Pattern GOODS_PATH = Pattern.compile("^/goods/(\\d+)(?:/.*)?$");
    private static final String ANONYMOUS_TOKEN_ENDPOINT = "GET ably-anonymous-token";
    private static final String BASIC_ENDPOINT = "GET ably-product-basic";
    private static final String DETAIL_ENDPOINT = "GET ably-product-detail";
    private static final String OPTIONS_ENDPOINT = "GET ably-product-options";

    private final ExternalApiClient api;
    private final ObjectMapper objectMapper;

    public AblyProductApiSupplementExtractor(
            ExternalApiClientFactory factory,
            ObjectMapper objectMapper
    ) {
        String deviceId = UUID.randomUUID().toString();
        this.api = factory.create(
                "link-extract",
                HttpClient.Redirect.NEVER,
                builder -> builder
                        .baseUrl(ABLY_API_BASE_URL)
                        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                        .defaultHeader(HttpHeaders.USER_AGENT, "OtbooProductExtractor/1.0")
                        .defaultHeader("X-Device-Type", "PCWeb")
                        .defaultHeader("X-App-Version", "0.1.0")
                        .defaultHeader("X-Web-Type", "Web")
                        .defaultHeader("X-Device-Id", deviceId));
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(URI productUri) {
        if (productUri == null || productUri.getHost() == null || productUri.getPath() == null) {
            return false;
        }
        String host = productUri.getHost().toLowerCase(Locale.ROOT);
        return ABLY_PRODUCT_HOSTS.contains(host)
                && GOODS_PATH.matcher(productUri.getPath()).matches();
    }

    @Override
    public ProductPageSupplement extract(Document document, URI productUri) {
        String productId = productId(productUri);
        if (productId == null) {
            return ProductPageSupplement.empty();
        }

        String anonymousToken = text(requestJson(
                ANONYMOUS_TOKEN_ENDPOINT,
                "/api/v2/anonymous/token/",
                null).get("token"));
        if (anonymousToken == null) {
            return ProductPageSupplement.empty();
        }

        JsonNode basic = requestJson(
                BASIC_ENDPOINT,
                "/api/v3/goods/" + productId + "/basic/",
                anonymousToken);
        JsonNode detail = requestJson(
                DETAIL_ENDPOINT,
                "/api/v3/goods/" + productId + "/detail/",
                anonymousToken);
        JsonNode options = requestJson(
                OPTIONS_ENDPOINT,
                "/api/v2/goods/" + productId + "/options/",
                anonymousToken);

        List<URI> coverImages = collectUris(basic.at("/goods/cover_images"), productUri);
        Set<String> descriptions = new LinkedHashSet<>();
        Set<URI> detailImages = new LinkedHashSet<>();
        collectDescriptionParts(
                detail.at("/goods/detail_html_parts"),
                productUri,
                descriptions,
                detailImages);

        return new ProductPageSupplement(
                List.copyOf(descriptions),
                coverImages.isEmpty() ? null : coverImages.get(0),
                List.copyOf(detailImages),
                collectOptionTexts(options));
    }

    private JsonNode requestJson(String endpoint, String path, String anonymousToken) {
        String body = api.exchange(endpoint, client -> {
            if (anonymousToken == null) {
                return client.get().uri(path).retrieve().body(String.class);
            }
            return client.get()
                    .uri(path)
                    .header("X-Anonymous-Token", anonymousToken)
                    .retrieve()
                    .body(String.class);
        });
        if (body == null || body.isBlank()) {
            return objectMapper.missingNode();
        }
        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException ignored) {
            return objectMapper.missingNode();
        }
    }

    private void collectDescriptionParts(
            JsonNode parts,
            URI baseUri,
            Set<String> descriptions,
            Set<URI> images
    ) {
        if (!parts.isArray()) {
            return;
        }
        for (JsonNode part : parts) {
            if (!"DESCRIPTION".equals(text(part.get("html_part_type")))) {
                continue;
            }
            JsonNode contents = part.get("contents");
            if (contents == null) {
                continue;
            }
            if (contents.isArray()) {
                contents.forEach(content -> collectDescription(content, baseUri, descriptions, images));
            } else {
                collectDescription(contents, baseUri, descriptions, images);
            }
        }
    }

    private void collectDescription(
            JsonNode content,
            URI baseUri,
            Set<String> descriptions,
            Set<URI> images
    ) {
        String html = text(content);
        if (html == null) {
            return;
        }
        Document fragment = Jsoup.parseBodyFragment(html, baseUri.toString());
        String description = cleanText(fragment.text());
        if (description != null) {
            descriptions.add(description);
        }
        for (Element image : fragment.select("img")) {
            URI imageUri = resolveHttpsImage(
                    firstNonBlank(image.attr("src"), image.attr("data-src")),
                    baseUri);
            if (imageUri != null) {
                images.add(imageUri);
            }
        }
    }

    private List<String> collectOptionTexts(JsonNode root) {
        List<JsonNode> groups = new ArrayList<>();
        if (root.isArray()) {
            root.forEach(groups::add);
        } else if (root.isObject()) {
            groups.add(root);
        }

        Set<String> options = new LinkedHashSet<>();
        for (JsonNode group : groups) {
            String optionName = text(group.get("name"));
            JsonNode components = group.get("option_components");
            if (optionName == null || components == null || !components.isArray()) {
                continue;
            }
            for (JsonNode component : components) {
                String value = text(component.get("name"));
                if (value != null) {
                    options.add(optionName + ": " + value);
                }
            }
        }
        return List.copyOf(options);
    }

    private List<URI> collectUris(JsonNode node, URI baseUri) {
        if (!node.isArray()) {
            return List.of();
        }
        Set<URI> uris = new LinkedHashSet<>();
        for (JsonNode value : node) {
            URI uri = resolveHttpsImage(text(value), baseUri);
            if (uri != null) {
                uris.add(uri);
            }
        }
        return List.copyOf(uris);
    }

    private String productId(URI productUri) {
        if (!supports(productUri)) {
            return null;
        }
        Matcher matcher = GOODS_PATH.matcher(productUri.getPath());
        return matcher.matches() ? matcher.group(1) : null;
    }

    private URI resolveHttpsImage(String source, URI baseUri) {
        if (source == null || source.isBlank()) {
            return null;
        }
        try {
            URI resolved = baseUri.resolve(source.trim());
            if (!"https".equalsIgnoreCase(resolved.getScheme()) || resolved.getHost() == null) {
                return null;
            }
            return resolved;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String text(JsonNode node) {
        return node != null && node.isTextual() ? cleanText(node.textValue()) : null;
    }

    private String cleanText(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("\\s+", " ").trim();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
