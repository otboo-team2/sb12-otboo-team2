package com.otboo.feed.search.elasticsearch;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 색인되는 피드 문서. <b>검색에 쓰이는 필드만 담는다.</b>
 *
 * <p>응답 본문은 {@code FeedViewLoader} 가 MySQL 에서 만든다. 여기에 작성자 프로필 이미지나
 * 의상 목록까지 넣으면 그 값이 바뀔 때마다 재색인해야 하고, 색인이 늦으면 <b>화면에 옛 값이 뜬다.</b>
 * 검색은 "어떤 피드인가"만 정하고 "무엇을 보여줄까"는 원본이 정한다.
 *
 * <p>{@code createdAt} 을 {@link Instant} 가 아니라 문자열로 들고 있는 이유는 JSON 직렬화가
 * 어떤 {@code ObjectMapper} 를 타든 형식이 흔들리지 않게 하기 위해서다. 색인 시각 형식이 바뀌면
 * 정렬과 커서가 조용히 어긋난다.
 *
 * @param createdAt ISO-8601 UTC. 마이크로초까지 남긴다({@code date_nanos} 매핑과 짝)
 */
public record FeedDocument(
        String id,
        String content,
        String authorId,
        String authorName,
        String skyStatus,
        String precipitationType,
        String createdAt,
        long likeCount
) {

    public static FeedDocument of(
            UUID id,
            String content,
            UUID authorId,
            String authorName,
            String skyStatus,
            String precipitationType,
            Instant createdAt,
            long likeCount
    ) {
        return new FeedDocument(
                id.toString(),
                content,
                authorId.toString(),
                authorName,
                skyStatus,
                precipitationType,
                DateTimeFormatter.ISO_INSTANT.format(createdAt),
                likeCount);
    }
}
