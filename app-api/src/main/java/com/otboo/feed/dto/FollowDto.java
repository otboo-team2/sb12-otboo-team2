package com.otboo.feed.dto;

import java.util.UUID;

/**
 * 팔로우 한 건.
 *
 * <p>팔로우 취소 -> {@code DELETE /api/follows/{followId}}
 * <b>목록 응답에 {@code id} 가 반드시 실려야 함. 없으면 프론트가 취소할 대상을 지목하지 못함.
 *
 * @param id       {@code follows.id}
 * @param followee 팔로우 <b>당하는</b> 사람
 * @param follower 팔로우 <b>하는</b> 사람
 */
public record FollowDto(
        UUID id,
        UserSummary followee,
        UserSummary follower
) {
}
