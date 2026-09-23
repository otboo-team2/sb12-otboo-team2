package com.otboo.feed.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 피드 응답. <b>필드명이 프론트 {@code FeedDto} 와 정확히 일치해야 한다.</b>
 *
 * <p>엔티티에서 바로 만들지 않는다. 작성자 이름·프로필 이미지·날씨·착장은 모두 다른 테이블에 있어
 * 엔티티 그래프를 타면 피드 20건에 수십 번의 추가 쿼리가 붙는다.
 * {@code FeedViewLoader} 가 페이지 전체를 고정된 쿼리 몇 번으로 읽어 이 DTO 를 만든다.
 *
 * @param likedByMe 비로그인 조회에서는 항상 false
 */
public record FeedDto(
        UUID id,
        Instant createdAt,
        Instant updatedAt,
        AuthorDto author,
        WeatherSummaryDto weather,
        List<OotdDto> ootds,
        String content,
        Long likeCount,
        int commentCount,
        boolean likedByMe
) {
}
