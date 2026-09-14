package com.otboo.clothes.extraction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.clothes.exception.ClothesErrorCode;
import com.otboo.common.exception.BusinessException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

/** JSON-LD, Open Graph와 일반 HTML을 순서대로 살펴 상품 페이지의 공통 정보를 수집한다. */
@Component
public class ProductPageExtractor {

    private static final int MAX_IMAGE_CANDIDATES = 20;
    private static final int MIN_IMAGE_DIMENSION = 100;
    private static final List<String> DECOY_KEYWORDS = List.of(
            "logo", "icon", "banner", "review", "recommend");

    private final SafeRemoteResourceClient remoteClient;
    private final ObjectMapper objectMapper;
    private final ClothesExtractionProperties properties;

    public ProductPageExtractor(
            SafeRemoteResourceClient remoteClient,
            ObjectMapper objectMapper,
            ClothesExtractionProperties properties
    ) {
        this.remoteClient = remoteClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public ProductPageData extract(URI productUrl) {
        RemoteResource resource = remoteClient.getHtml(productUrl);
        Document document = parse(resource);

        JsonLdProduct jsonLdProduct = findProductFromJsonLd(document);
        String name = firstNonBlank(
                jsonLdProduct.name(),
                metaContent(document, "property", "og:title"),
                firstElementText(document.select("h1")),
                document.title());
        String description = firstNonBlank(
                jsonLdProduct.description(),
                metaContent(document, "property", "og:description"),
                metaContent(document, "name", "description"),
                firstElementText(document.select("[itemprop=description]")),
                bodyText(document));

        List<URI> structuredImages = jsonLdProduct.imageUrls();
        URI openGraphImage = resolveImage(
                metaContent(document, "property", "og:image"), resource.finalUri());
        List<ImageCandidate> htmlImages = collectHtmlImages(document, resource.finalUri());

        List<URI> imageCandidates = new ArrayList<>();
        if (!structuredImages.isEmpty()) {
            imageCandidates.addAll(structuredImages);
        } else if (openGraphImage != null) {
            imageCandidates.add(openGraphImage);
        }
        for (ImageCandidate candidate : htmlImages) {
            if (imageCandidates.size() >= MAX_IMAGE_CANDIDATES) {
                break;
            }
            if (!imageCandidates.contains(candidate.uri())) {
                imageCandidates.add(candidate.uri());
            }
        }

        URI primaryImage = imageCandidates.isEmpty() ? null : imageCandidates.get(0);
        if (primaryImage == null && openGraphImage != null && structuredImages.isEmpty()) {
            primaryImage = openGraphImage;
        }
        if (isBlank(name) && primaryImage == null) {
            throw new BusinessException(ClothesErrorCode.PRODUCT_DATA_NOT_FOUND);
        }

        URI selectedPrimaryImage = primaryImage;
        List<URI> detailImages = selectedPrimaryImage == null
                ? imageCandidates
                : imageCandidates.stream()
                        .filter(image -> !image.equals(selectedPrimaryImage))
                        .toList();
        return new ProductPageData(
                resource.finalUri(),
                name,
                cap(description),
                primaryImage,
                detailImages,
                collectOptionTexts(document));
    }

    private Document parse(RemoteResource resource) {
        try (InputStream input = new ByteArrayInputStream(resource.body())) {
            return Jsoup.parse(input, null, resource.finalUri().toString());
        } catch (IOException exception) {
            throw new BusinessException(ClothesErrorCode.PRODUCT_DATA_NOT_FOUND, exception);
        }
    }

    private JsonLdProduct findProductFromJsonLd(Document document) {
        for (Element script : document.select("script[type=application/ld+json]")) {
            String json = script.data();
            if (isBlank(json)) {
                continue;
            }
            try {
                JsonNode root = objectMapper.readTree(json);
                JsonNode product = findProductNode(root);
                if (product != null) {
                    return new JsonLdProduct(
                            textNode(product.get("name")),
                            textNode(product.get("description")),
                            imageUrls(product.get("image"), document.baseUri()));
                }
            } catch (JsonProcessingException ignored) {
                // 한 블록이 깨져 있어도 Open Graph와 다른 JSON-LD 블록은 계속 확인한다.
            }
        }
        return JsonLdProduct.empty();
    }

    private JsonNode findProductNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                JsonNode product = findProductNode(child);
                if (product != null) {
                    return product;
                }
            }
            return null;
        }
        if (!node.isObject()) {
            return null;
        }
        if (isProductType(node.get("@type"))) {
            return node;
        }
        JsonNode graph = node.get("@graph");
        if (graph != null) {
            JsonNode product = findProductNode(graph);
            if (product != null) {
                return product;
            }
        }
        var fields = node.fields();
        while (fields.hasNext()) {
            JsonNode product = findProductNode(fields.next().getValue());
            if (product != null) {
                return product;
            }
        }
        return null;
    }

    private boolean isProductType(JsonNode typeNode) {
        if (typeNode == null) {
            return false;
        }
        if (typeNode.isArray()) {
            for (JsonNode type : typeNode) {
                if (isProductType(type)) {
                    return true;
                }
            }
            return false;
        }
        return typeNode.isTextual() && "product".equalsIgnoreCase(typeNode.textValue());
    }

    private List<URI> imageUrls(JsonNode imageNode, String baseUri) {
        if (imageNode == null || imageNode.isNull()) {
            return List.of();
        }
        List<URI> images = new ArrayList<>();
        collectJsonLdImages(imageNode, images, URI.create(baseUri));
        return distinct(images).stream()
                .limit(MAX_IMAGE_CANDIDATES)
                .toList();
    }

    private void collectJsonLdImages(JsonNode imageNode, List<URI> images, URI baseUri) {
        if (imageNode.isArray()) {
            imageNode.forEach(image -> collectJsonLdImages(image, images, baseUri));
            return;
        }
        String imageValue = textNode(imageNode);
        if (imageValue != null) {
            URI image = resolveImage(imageValue, baseUri);
            if (image != null) {
                images.add(image);
            }
            return;
        }
        if (imageNode.isObject()) {
            String url = firstNonBlank(
                    textNode(imageNode.get("url")),
                    textNode(imageNode.get("contentUrl")));
            if (url != null) {
                URI image = resolveImage(url, baseUri);
                if (image != null) {
                    images.add(image);
                }
            }
        }
    }

    private List<ImageCandidate> collectHtmlImages(Document document, URI baseUri) {
        List<ImageCandidate> images = new ArrayList<>();
        Set<URI> seen = new LinkedHashSet<>();
        for (Element imageElement : document.select("img")) {
            if (isDecoy(imageElement) || isTiny(imageElement)) {
                continue;
            }
            List<String> rawSources = new ArrayList<>();
            rawSources.add(imageElement.attr("src"));
            rawSources.add(imageElement.attr("data-src"));
            rawSources.add(firstSrcsetCandidate(imageElement.attr("srcset")));
            for (String rawSource : rawSources) {
                URI image = resolveImage(rawSource, baseUri);
                if (image != null
                        && !containsDecoyKeyword(image.toString())
                        && seen.add(image)) {
                    images.add(new ImageCandidate(image));
                    if (images.size() >= MAX_IMAGE_CANDIDATES) {
                        return images;
                    }
                    break;
                }
            }
        }
        return images;
    }

    private boolean isDecoy(Element imageElement) {
        StringBuilder context = new StringBuilder();
        Element current = imageElement;
        int depth = 0;
        while (current != null && depth++ < 4) {
            context.append(' ')
                    .append(current.className())
                    .append(' ')
                    .append(current.id())
                    .append(' ')
                    .append(current.attr("alt"))
                    .append(' ')
                    .append(current.attr("title"));
            current = current.parent();
        }
        String normalized = context.toString().toLowerCase(Locale.ROOT);
        return containsDecoyKeyword(normalized);
    }

    private boolean containsDecoyKeyword(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return DECOY_KEYWORDS.stream().anyMatch(normalized::contains);
    }

    private boolean isTiny(Element imageElement) {
        return isBelowMinimum(imageElement.attr("width"))
                || isBelowMinimum(imageElement.attr("height"));
    }

    private boolean isBelowMinimum(String dimension) {
        if (isBlank(dimension)) {
            return false;
        }
        try {
            return Integer.parseInt(dimension.trim()) < MIN_IMAGE_DIMENSION;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private String firstSrcsetCandidate(String srcset) {
        if (isBlank(srcset)) {
            return null;
        }
        String firstCandidate = srcset.split(",", 2)[0].trim();
        if (firstCandidate.isEmpty()) {
            return null;
        }
        return firstCandidate.split("\\s+", 2)[0];
    }

    private URI resolveImage(String rawSource, URI baseUri) {
        if (isBlank(rawSource)) {
            return null;
        }
        String source = rawSource.trim();
        if (source.regionMatches(true, 0, "data:", 0, 5)) {
            return null;
        }
        try {
            URI resolved = baseUri == null ? URI.create(source) : baseUri.resolve(source);
            if (!"https".equalsIgnoreCase(resolved.getScheme())
                    || resolved.getHost() == null) {
                return null;
            }
            return resolved;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private List<String> collectOptionTexts(Document document) {
        Set<String> options = new LinkedHashSet<>();
        for (Element option : document.select("select option")) {
            String text = cleanText(option.text());
            if (!isBlank(text)) {
                options.add(text);
            }
        }
        return List.copyOf(options);
    }

    private String bodyText(Document document) {
        if (document.body() == null) {
            return null;
        }
        return cleanText(document.body().text());
    }

    private String metaContent(Document document, String attribute, String value) {
        Element meta = document.selectFirst("meta[" + attribute + "=" + value + "]");
        return meta == null ? null : cleanText(meta.attr("content"));
    }

    private String firstElementText(Elements elements) {
        for (Element element : elements) {
            String text = cleanText(element.text());
            if (!isBlank(text)) {
                return text;
            }
        }
        return null;
    }

    private String textNode(JsonNode node) {
        if (node == null || node.isNull() || !node.isValueNode()) {
            return null;
        }
        String text = cleanText(node.asText());
        return isBlank(text) ? null : text;
    }

    private String cap(String value) {
        if (isBlank(value)) {
            return null;
        }
        int max = properties.maxPageTextChars();
        if (max <= 0) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private static String cleanText(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("\\s+", " ").trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static List<URI> distinct(List<URI> values) {
        return List.copyOf(new LinkedHashSet<>(values));
    }

    private record JsonLdProduct(String name, String description, List<URI> imageUrls) {
        private static JsonLdProduct empty() {
            return new JsonLdProduct(null, null, List.of());
        }
    }

    private record ImageCandidate(URI uri) {
    }
}
