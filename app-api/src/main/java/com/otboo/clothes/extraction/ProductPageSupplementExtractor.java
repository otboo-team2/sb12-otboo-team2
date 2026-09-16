package com.otboo.clothes.extraction;

import java.net.URI;
import org.jsoup.nodes.Document;

/** 상품 페이지별로 범용 추출 결과에서 빠진 정보를 보충한다. */
public interface ProductPageSupplementExtractor {

    boolean supports(URI productUrl);

    ProductPageSupplement extract(Document document, URI productUrl);
}
