package com.otboo.feed.dto;

import java.util.UUID;

/**
 * 팔로우 응답에 실리는 사용자 요약. {@code FollowDto} 의 {@code followee}·{@code follower} 가 이 모양이다.
 *
 * <p>필드는 피드의 {@code AuthorDto} 와 같지만 이름 다름
 * 프론트 타입도 둘로 나뉘어 있어 그대로 따름.
 *
 * @param name            {@code users.name}
 * @param profileImageUrl {@code profiles.profile_image_url} — 프로필이 없으면 null
 */
public record UserSummary(
        UUID userId,
        String name,
        String profileImageUrl
) {
}
