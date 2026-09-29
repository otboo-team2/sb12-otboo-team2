package com.otboo.pinterest.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Pinterest v5 Board 상세 응답에서 필요한 필드만 받는다.
 *
 * <p>보드 description 에 {@code @otboo} 태그를 한 번 달아두면, 그 보드의 핀 전체가
 * 이 태그를 상속받는다({@link com.otboo.pinterest.tag.OutfitTagParser#parseMerged}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PinterestBoardResponse(String id, String name, String description) {
}
