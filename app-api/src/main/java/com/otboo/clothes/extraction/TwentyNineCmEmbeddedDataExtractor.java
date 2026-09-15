package com.otboo.clothes.extraction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/** 29CM의 Next.js Flight 데이터에 포함된 상품 상세 이미지 주소를 보충한다. */
@Component
public class TwentyNineCmEmbeddedDataExtractor implements ProductPageSupplementExtractor {

    private static final String TWENTY_NINE_CM_HOST = "29cm.co.kr";
    private static final String FLIGHT_DATA_PREFIX = "self.__next_f.push(";
    private static final List<String> DECOY_KEYWORDS = List.of("notice", "banner");

    private final ObjectMapper objectMapper;

    public TwentyNineCmEmbeddedDataExtractor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ProductPageSupplement extract(Document document, URI productUri) {
        if (!supports(productUri)) {
            return ProductPageSupplement.empty();
        }

        Set<URI> images = new LinkedHashSet<>();
        for (Element script : document.select("script")) {
            JsonNode flightData = parseFlightData(script.data());
            if (flightData != null) {
                collectImages(flightData, productUri, images);
            }
        }
        return new ProductPageSupplement(List.of(), List.copyOf(images));
    }

    private JsonNode parseFlightData(String scriptData) {
        if (scriptData == null) {
            return null;
        }
        String trimmed = scriptData.trim();
        if (!trimmed.startsWith(FLIGHT_DATA_PREFIX)) {
            return null;
        }

        int payloadStart = FLIGHT_DATA_PREFIX.length();
        int payloadEnd = trimmed.lastIndexOf(')');
        if (payloadEnd <= payloadStart) {
            return null;
        }
        try {
            return objectMapper.readTree(trimmed.substring(payloadStart, payloadEnd));
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }

    private void collectImages(JsonNode node, URI baseUri, Set<URI> images) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isTextual()) {
            collectImagesFromFragment(node.textValue(), baseUri, images);
            return;
        }
        if (node.isContainerNode()) {
            node.forEach(child -> collectImages(child, baseUri, images));
        }
    }

    private void collectImagesFromFragment(String fragment, URI baseUri, Set<URI> images) {
        if (fragment == null || !fragment.contains("<img")) {
            return;
        }
        Document detailDocument = Jsoup.parseBodyFragment(fragment, baseUri.toString());
        for (Element image : detailDocument.select("img")) {
            String source = firstNonBlank(image.attr("src"), image.attr("data-src"));
            URI resolved = resolveHttpsImage(source, baseUri);
            if (resolved != null && !containsDecoyKeyword(resolved)) {
                images.add(resolved);
            }
        }
    }

    private URI resolveHttpsImage(String source, URI baseUri) {
        if (source == null || source.isBlank()) {
            return null;
        }
        try {
            URI resolved = baseUri.resolve(source.trim());
            if ("http".equalsIgnoreCase(resolved.getScheme())) {
                resolved = new URI(
                        "https",
                        resolved.getUserInfo(),
                        resolved.getHost(),
                        resolved.getPort(),
                        resolved.getPath(),
                        resolved.getQuery(),
                        resolved.getFragment());
            }
            if (!"https".equalsIgnoreCase(resolved.getScheme()) || resolved.getHost() == null) {
                return null;
            }
            return resolved;
        } catch (IllegalArgumentException | URISyntaxException ignored) {
            return null;
        }
    }

    private boolean containsDecoyKeyword(URI imageUri) {
        String normalized = imageUri.toString().toLowerCase(Locale.ROOT);
        return DECOY_KEYWORDS.stream().anyMatch(normalized::contains);
    }

    @Override
    public boolean supports(URI productUri) {
        if (productUri == null || productUri.getHost() == null) {
            return false;
        }
        String host = productUri.getHost().toLowerCase(Locale.ROOT);
        return host.equals(TWENTY_NINE_CM_HOST)
                || host.endsWith("." + TWENTY_NINE_CM_HOST);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
