package com.otboo.clothes.extraction;

import java.net.URI;
import java.util.List;

/** 범용 상품 페이지에서 수집한 아직 AI 분석 전의 상품 정보다. */
public record ProductPageData(
        URI productUrl,
        String name,
        String description,
        URI imageUrl,
        List<URI> detailImageUrls,
        List<String> optionTexts
) {

    public ProductPageData {
        detailImageUrls = detailImageUrls == null ? List.of() : List.copyOf(detailImageUrls);
        optionTexts = optionTexts == null ? List.of() : List.copyOf(optionTexts);
    }
}
