package com.otboo.clothes.extraction;

import java.net.URI;
import java.util.List;

/** 사이트별 보조 추출기가 공통 추출기에 전달하는 추가 정보다. */
public record ProductPageSupplement(
        List<String> descriptions,
        URI primaryImageUrl,
        List<URI> detailImageUrls,
        List<String> optionTexts
) {

    public ProductPageSupplement {
        descriptions = descriptions == null ? List.of() : List.copyOf(descriptions);
        detailImageUrls = detailImageUrls == null ? List.of() : List.copyOf(detailImageUrls);
        optionTexts = optionTexts == null ? List.of() : List.copyOf(optionTexts);
    }

    public static ProductPageSupplement empty() {
        return new ProductPageSupplement(List.of(), null, List.of(), List.of());
    }
}
