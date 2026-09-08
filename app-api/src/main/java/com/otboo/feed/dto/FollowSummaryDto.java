package com.otboo.feed.dto;

import java.util.UUID;

/**
 * 프로필 화면의 팔로우 요약.
 *
 * <p>기준이 두 개 섞여 있어 헷갈리기 쉽다 — <b>{@code followeeId} 는 조회 대상</b>이고,
 * {@code followedByMe}·{@code followingMe} 는 <b>로그인한 나</b>와의 관계다.
 *
 * @param followeeId     조회 대상 사용자
 * @param followerCount  대상을 팔로우하는 사람 수
 * @param followingCount 대상이 팔로우하는 사람 수
 * @param followedByMe   내가 대상을 팔로우하고 있는지
 * @param followedByMeId 그 팔로우의 id. 취소({@code DELETE /api/follows/{followId}})에 쓰므로
 *                       <b>{@code followedByMe} 가 true 면 반드시 채워야 한다.</b> 아니면 null
 * @param followingMe    대상이 나를 팔로우하고 있는지 (맞팔 표시용)
 */
public record FollowSummaryDto(
        UUID followeeId,
        long followerCount,
        long followingCount,
        boolean followedByMe,
        UUID followedByMeId,
        boolean followingMe
) {
}
