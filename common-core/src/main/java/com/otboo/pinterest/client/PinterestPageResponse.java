package com.otboo.pinterest.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Pinterest v5 목록 응답. {@code bookmark} 가 {@code null} 이면 마지막 페이지다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PinterestPageResponse<T>(List<T> items, String bookmark) {

    public PinterestPageResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public boolean hasNext() {
        return bookmark != null && !bookmark.isBlank();
    }
}
