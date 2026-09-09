package com.otboo.feed.dto;

import java.util.UUID;

/**
 * 작성자 요약. 이름은 {@code users.name}, 이미지는 {@code profiles.profile_image_url} 에서 온다.
 *
 * <p>팔로우·DM 파트도 같은 모양을 쓴다. 그 파트들이 들어오면 공용 패키지로 올리고
 * 여기서는 import 만 바꾸면 된다. 지금 공용 패키지에 미리 만들면 남의 파트와 충돌한다.
 */
public record AuthorDto(
        UUID userId,
        String name,
        String profileImageUrl
) {
}
