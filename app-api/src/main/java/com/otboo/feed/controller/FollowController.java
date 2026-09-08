package com.otboo.feed.controller;

import com.otboo.common.pagination.CursorRequest;
import com.otboo.common.pagination.CursorResponse;
import com.otboo.common.security.AuthPrincipal;
import com.otboo.common.security.LoginUser;
import com.otboo.feed.dto.FollowCreateRequest;
import com.otboo.feed.dto.FollowDto;
import com.otboo.feed.dto.FollowSummaryDto;
import com.otboo.feed.service.FollowService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 팔로우 API
 * <p>목록의 {@code followerId}·{@code followeeId} 는 <b>조회 대상</b>이라 성격이 다르다.
 * 남의 팔로잉 목록을 보는 것은 정상 동작이므로 쿼리 파라미터로 그대로 받는다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/follows")
public class FollowController {

    private final FollowService followService;

    @PostMapping
    public ResponseEntity<FollowDto> createFollow(
            @LoginUser AuthPrincipal me,
            @Valid @RequestBody FollowCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(followService.follow(me, request));
    }

    @DeleteMapping("/{followId}")
    public ResponseEntity<Void> cancelFollow(
            @LoginUser AuthPrincipal me,
            @PathVariable UUID followId
    ) {
        followService.unfollow(me, followId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 팔로우 요약.
     *
     * <p>{@code userId} 는 조회 대상
     * {@code followedByMe}·{@code followingMe} 는 <b>로그인한 나</b>와의 관계다.
     * 비로그인도 볼 수 있어야 해서 {@code required = false}.
     */
    @GetMapping("/summary")
    public FollowSummaryDto getFollowSummary(
            @RequestParam UUID userId,
            @LoginUser(required = false) AuthPrincipal me
    ) {
        return followService.findSummary(me, userId);
    }

    /**
     * 팔로잉 목록 — {@code followerId} 가 팔로우하는 사람들.
     *
     * {@code cursor · idAfter · limit} 을 한꺼번에 받는다
     * ({@link CursorRequest}) 스펙에 {@code sortBy} 가 없어 정렬은 서버가
     * {@code createdAt DESC} 로 고정 셋팅.
     */
    @GetMapping("/followings")
    public CursorResponse<FollowDto> getFollowings(
            @RequestParam UUID followerId,
            CursorRequest page,
            @RequestParam(required = false) String nameLike
    ) {
        return followService.findFollowings(followerId, page, nameLike);
    }

    /** 팔로워 목록 */
    @GetMapping("/followers")
    public CursorResponse<FollowDto> getFollowers(
            @RequestParam UUID followeeId,
            CursorRequest page,
            @RequestParam(required = false) String nameLike
    ) {
        return followService.findFollowers(followeeId, page, nameLike);
    }
}
