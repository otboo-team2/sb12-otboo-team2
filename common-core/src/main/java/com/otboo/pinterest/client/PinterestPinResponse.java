package com.otboo.pinterest.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Pinterest v5 Pin 응답에서 인덱싱에 필요한 필드만 받음.
 * <p>{@code metrics} · {@code board_owner} 등 나머지는 받지 않는다.
 * 쓰지 않는 필드를 매핑해두면 Pinterest 가 그 필드 형식을 바꿨을 때 쓰지도 않는 값 때문에 역직렬화가 깨짐.
 *
 * <p>{@code created_at} 도 그래서 받지 않는다. 스펙은 {@code date-time} 이라고만 적혀 있고
 * 실제 응답은 {@code 2026-09-01T03:04:05} 처럼 타임존이 없을 수 있어 {@code Instant} 로 받으면
 * <b>핀 목록 전체</b>가 역직렬화에 실패한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PinterestPinResponse(
        String id,
        @JsonProperty("board_id") String boardId,
        String link,
        String title,
        String description,
        @JsonProperty("alt_text") String altText,
        Media media
) {

    /**
     * 목록에 쓸 이미지 주소. 큰 것부터 고른다.
     *
     * <p>단일 이미지 핀만 다룬다. 영상·여러 장짜리 핀은 {@code media_type} 이 달라 {@code images} 가
     * 없으므로 빈 값이 나오고, 동기화 쪽에서 건너뛴다. 코디 추천은 사진 한 장이면 충분하다.
     */
    public Optional<String> imageUrl() {
        if (media == null || !"image".equals(media.mediaType()) || media.images() == null) {
            return Optional.empty();
        }
        Images images = media.images();
        return Stream.of(images.w1200(), images.w600(), images.w400x300(), images.w150x150())
                .filter(image -> image != null && image.url() != null && !image.url().isBlank())
                .map(Image::url)
                .findFirst();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Media(
            @JsonProperty("media_type") String mediaType,
            Images images
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Images(
            @JsonProperty("1200x") Image w1200,
            @JsonProperty("600x") Image w600,
            @JsonProperty("400x300") Image w400x300,
            @JsonProperty("150x150") Image w150x150
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Image(String url, Integer width, Integer height) {
    }
}
