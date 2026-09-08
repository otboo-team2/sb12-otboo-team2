package com.otboo.feed.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * 팔로우 생성 요청.
 *
 * <p>Swagger 에는 {@code followerId} 도 있지만 <b>받지 않는다.</b> 팔로우하는 주체는 언제나
 * 토큰의 사용자다. 본문으로 받으면 <b>남이 나를 팔로우한 것처럼 만들 수 있다.</b>
 * {@code common-core} 의 {@code LoginUser} 주석이 {@code authorId · ownerId · followerId} 를
 * 콕 집어 "절대 신뢰하지 않는다" 고 못박아 둔 것과 같은 이유이고,
 * {@code FeedCreateRequest} 에서 {@code authorId} 를 뺀 것과 같은 규칙이다.
 *
 * <p>자기 자신을 팔로우하는 것은 DB 의 {@code ck_follows_not_self} 가 막지만, 그대로 두면
 * 500 이 나가므로 서비스에서 먼저 걸러야 한다. 중복 팔로우도 {@code uk_follows_follower_followee}
 * 위반을 잡아 409 로 돌려주는 편이 정확하다(좋아요와 같은 구조).
 */
public record FollowCreateRequest(
        @NotNull(message = "팔로우할 사용자를 지정해 주세요.")
        UUID followeeId
) {
}
