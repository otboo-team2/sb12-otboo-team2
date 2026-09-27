package com.otboo.clothes.extraction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/** 무신사 페이지의 내장 상태 데이터에서 상품 설명과 상세 이미지 주소를 보충한다. */
@Component
public class MusinsaEmbeddedDataExtractor implements ProductPageSupplementExtractor {

    private static final String MUSINSA_HOST = "musinsa.com";
    private static final String PRODUCT_DATA_PATH = "/props/pageProps/meta/data";
    private static final Set<String> DETAIL_IMAGE_FIELDS = Set.of(
            "goodsContents", "detailImages", "detailImageUrl");

    private final ObjectMapper objectMapper;

    public MusinsaEmbeddedDataExtractor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ProductPageSupplement extract(Document document, URI productUri) {
        if (!supports(productUri)) {
            return ProductPageSupplement.empty();
        }
        Element nextData = document.selectFirst("script#__NEXT_DATA__");
        if (nextData == null || nextData.data().isBlank()) {
            return ProductPageSupplement.empty();
        }

        try {
            JsonNode product = objectMapper.readTree(nextData.data()).at(PRODUCT_DATA_PATH);
            if (!product.isObject()) {
                return ProductPageSupplement.empty();
            }
            Set<String> descriptions = new LinkedHashSet<>();
            collectTextValues(product.get("goodsMaterial"), descriptions);
            addText(descriptions, textValue(product.get("mdOpinion")));

            List<URI> detailImages = new ArrayList<>();
            collectDetailImageFields(product, productUri, descriptions, detailImages);
            return new ProductPageSupplement(
                    List.copyOf(descriptions),
                    null,
                    distinct(detailImages),
                    List.of());
        } catch (JsonProcessingException ignored) {
            return ProductPageSupplement.empty();
        }
    }

    private void collectDetailImageFields(
            JsonNode node,
            URI productUri,
            Set<String> descriptions,
            List<URI> images
    ) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> collectDetailImageFields(
                    child, productUri, descriptions, images));
            return;
        }
        if (!node.isObject()) {
            return;
        }

        var fields = node.fields();
        while (fields.hasNext()) {
            var field = fields.next();
            if (isExcludedDetailField(field.getKey())) {
                continue;
            }
            if (DETAIL_IMAGE_FIELDS.contains(field.getKey())) {
                collectDetailFieldValue(
                        field.getKey(), field.getValue(), productUri, descriptions, images);
            } else if (field.getValue().isContainerNode()) {
                collectDetailImageFields(
                        field.getValue(), productUri, descriptions, images);
            }
        }
    }

    private void collectDetailFieldValue(
            String fieldName,
            JsonNode value,
            URI productUri,
            Set<String> descriptions,
            List<URI> images
    ) {
        if (value == null || value.isNull()) {
            return;
        }
        if (value.isTextual()) {
            String text = value.textValue().trim();
            if ("detailImageUrl".equals(fieldName) || looksLikeImageUrl(text)) {
                URI resolved = resolveHttpsImage(text, productUri);
                if (resolved != null) {
                    images.add(resolved);
                }
            } else {
                collectImagesFromFragment(text, productUri, descriptions, images);
            }
            return;
        }
        if (value.isArray()) {
            value.forEach(child -> collectDetailFieldValue(
                    fieldName, child, productUri, descriptions, images));
            return;
        }
        if (value.isObject()) {
            var fields = value.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                if (isExcludedDetailField(field.getKey())) {
                    continue;
                }
                if (DETAIL_IMAGE_FIELDS.contains(field.getKey())) {
                    collectDetailFieldValue(
                            field.getKey(), field.getValue(), productUri, descriptions, images);
                } else if (field.getValue().isContainerNode()) {
                    collectDetailFieldValue(
                            fieldName, field.getValue(), productUri, descriptions, images);
                } else if (field.getValue().isTextual()) {
                    String text = field.getValue().textValue().trim();
                    if (looksLikeHtmlFragment(text) || looksLikeImageUrl(text)) {
                        collectDetailFieldValue(
                                fieldName, field.getValue(), productUri, descriptions, images);
                    }
                }
            }
        }
    }

    private void collectImagesFromFragment(
            String fragmentHtml,
            URI productUri,
            Set<String> descriptions,
            List<URI> images
    ) {
        if (fragmentHtml.isBlank()) {
            return;
        }
        Document fragment = Jsoup.parseBodyFragment(fragmentHtml, productUri.toString());
        if (fragment.body() != null) {
            addText(descriptions, cleanText(fragment.body().text()));
            collectImages(fragment, productUri, images);
        }
    }

    private boolean isExcludedDetailField(String fieldName) {
        String normalized = fieldName.toLowerCase(Locale.ROOT);
        return normalized.contains("review")
                || normalized.contains("banner")
                || normalized.contains("recommend")
                || normalized.contains("notice");
    }

    private boolean looksLikeImageUrl(String value) {
        return value.startsWith("https://")
                || value.startsWith("http://")
                || value.startsWith("//")
                || value.startsWith("/");
    }

    private boolean looksLikeHtmlFragment(String value) {
        return value.contains("<") && value.contains(">");
    }

    @Override
    public boolean supports(URI productUri) {
        if (productUri == null || productUri.getHost() == null) {
            return false;
        }
        String host = productUri.getHost().toLowerCase(java.util.Locale.ROOT);
        return host.equals(MUSINSA_HOST) || host.endsWith("." + MUSINSA_HOST);
    }

    private void collectTextValues(JsonNode node, Set<String> values) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isTextual()) {
            addText(values, cleanText(node.textValue()));
            return;
        }
        if (node.isContainerNode()) {
            node.forEach(child -> collectTextValues(child, values));
        }
    }

    private void collectImages(Document fragment, URI baseUri, List<URI> images) {
        for (Element image : fragment.select("img")) {
            String rawSource = firstNonBlank(
                    image.attr("data-original"),
                    image.attr("data-src"),
                    largestSrcsetCandidate(image.attr("srcset")),
                    image.attr("src"));
            URI resolved = resolveHttpsImage(rawSource, baseUri);
            if (resolved != null) {
                images.add(resolved);
            }
        }
    }

    private String largestSrcsetCandidate(String srcset) {
        if (srcset == null || srcset.isBlank()) {
            return null;
        }

        String firstSource = null;
        String largestSource = null;
        double largestSize = -1;
        for (String candidate : srcset.split(",")) {
            String[] parts = candidate.trim().split("\\s+", 2);
            if (parts.length == 0 || parts[0].isBlank()) {
                continue;
            }
            if (firstSource == null) {
                firstSource = parts[0];
            }
            if (parts.length < 2) {
                continue;
            }

            double size = srcsetSize(parts[1]);
            if (size > largestSize) {
                largestSource = parts[0];
                largestSize = size;
            }
        }
        return largestSource == null ? firstSource : largestSource;
    }

    private double srcsetSize(String descriptor) {
        String normalized = descriptor.trim().toLowerCase(Locale.ROOT);
        if (normalized.endsWith("w") || normalized.endsWith("x")) {
            try {
                return Double.parseDouble(normalized.substring(0, normalized.length() - 1));
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }

    private URI resolveHttpsImage(String rawSource, URI baseUri) {
        if (rawSource == null || rawSource.isBlank()) {
            return null;
        }
        try {
            URI resolved = baseUri.resolve(rawSource.trim());
            if (!"https".equalsIgnoreCase(resolved.getScheme()) || resolved.getHost() == null) {
                return null;
            }
            return resolved;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String textValue(JsonNode node) {
        return node != null && node.isTextual() ? cleanText(node.textValue()) : null;
    }

    private void addText(Set<String> values, String value) {
        if (value != null && !value.isBlank()) {
            values.add(value);
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

    private String cleanText(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("\\s+", " ").trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private List<URI> distinct(List<URI> values) {
        return List.copyOf(new LinkedHashSet<>(values));
    }

}
