package com.otboo.clothes.extraction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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
            String goodsContents = textValue(product.get("goodsContents"));
            if (goodsContents != null) {
                Document fragment = Jsoup.parseBodyFragment(
                        goodsContents,
                        productUri.toString());
                addText(descriptions, cleanText(fragment.text()));
                collectImages(fragment, productUri, detailImages);
            }
            return new ProductPageSupplement(
                    List.copyOf(descriptions),
                    null,
                    distinct(detailImages),
                    List.of());
        } catch (JsonProcessingException ignored) {
            return ProductPageSupplement.empty();
        }
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
            String rawSource = firstNonBlank(image.attr("src"), image.attr("data-src"));
            URI resolved = resolveHttpsImage(rawSource, baseUri);
            if (resolved != null) {
                images.add(resolved);
            }
        }
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
