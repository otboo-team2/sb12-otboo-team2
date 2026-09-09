package com.otboo.feed.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * 팔로우 생성 요청.
 *
 * <p>자기 자신을 팔로우하는 것은 DB 의 {@code ck_follows_not_self} 가 막지만, 그대로 두면
 * 500 이 나가므로 서비스에서 먼저 걸러야 한다.
 */
public record FollowCreateRequest(
        @NotNull(message = "팔로우할 사용자를 지정해 주세요.")
        UUID followeeId
) {
}
