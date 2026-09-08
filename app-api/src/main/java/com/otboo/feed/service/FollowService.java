package com.otboo.feed.service;

import com.otboo.common.event.FollowCreatedEvent;
import com.otboo.common.exception.BusinessException;
import com.otboo.common.pagination.CursorRequest;
import com.otboo.common.pagination.CursorResponse;
import com.otboo.common.pagination.SortDirection;
import com.otboo.common.security.AuthPrincipal;
import com.otboo.feed.dto.FollowCreateRequest;
import com.otboo.feed.dto.FollowDto;
import com.otboo.feed.dto.FollowSummaryDto;
import com.otboo.feed.entity.Follow;
import com.otboo.feed.exception.FollowErrorCode;
import com.otboo.feed.query.FollowViewLoader;
import com.otboo.feed.query.FollowViewLoader.FollowRow;
import com.otboo.feed.repository.FollowRepository;
import com.otboo.user.entity.User;
import com.otboo.user.exception.UserErrorCode;
import com.otboo.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 팔로우 · 언팔로우 · 목록 · 요약
 * 좋아요와 같은 구조다. "조회해서 없으면 저장" 은 버튼을 두 번 빠르게 누르면 둘 다 통과.
 * 미리 확인은 <b>빠른 실패용</b>일 뿐이고 진짜 방어는 유니크 제약 위반을 잡는 쪽.
 */
@Service
@RequiredArgsConstructor
public class FollowService {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final FollowViewLoader viewLoader;
    private final ApplicationEventPublisher eventPublisher;

    /** 목록 정렬은 서버가 고정한다. 스펙에 {@code sortBy} 가 없다. */
    private static final String SORT_BY_CREATED_AT = "createdAt";

    @Transactional
    public FollowDto follow(AuthPrincipal me, FollowCreateRequest request) {
        UUID followeeId = request.followeeId();

        // DB 의 ck_follows_not_self 까지 -> 500. 여기서 400 으로 끊음.
        if (followeeId.equals(me.userId())) {
            throw new BusinessException(FollowErrorCode.SELF_FOLLOW)
                    .addDetail("userId", me.userId().toString());
        }

        User follower = findUser(me.userId());
        User followee = findUser(followeeId);

        if (followRepository.existsByFollowerIdAndFolloweeId(me.userId(), followeeId)) {
            throw alreadyFollowing(followeeId, null);
        }

        Follow saved;
        try {
            saved = followRepository.saveAndFlush(Follow.of(follower, followee));
        } catch (DataIntegrityViolationException e) {
            // 확인과 저장 사이에 다른 요청이 먼저 insert.
            throw alreadyFollowing(followeeId, e);
        }

        eventPublisher.publishEvent(FollowCreatedEvent.of(me.userId(), followeeId));

        return viewLoader.loadOne(saved.getId());
    }

    /**
     * 팔로우 취소.
     *
     * <p>스펙이 {@code followId} 로 지우게 되어 있어 <b>남의 팔로우 id 를 넣으면 남의 관계를
     * 끊을 수 있다.</b> 그래서 주인 확인이 반드시 필요.
     */
    @Transactional
    public void unfollow(AuthPrincipal me, UUID followId) {
        UUID ownerId = followRepository.findFollowerIdById(followId)
                .orElseThrow(() -> notFound(followId));

        if (me.isNot(ownerId)) {
            throw new BusinessException(FollowErrorCode.NOT_FOLLOWER)
                    .addDetail("followId", followId.toString());
        }

        // 좋아요 취소와 같은 규칙 — 지워진 행 수로 판단한다.
        // 읽고 나서 지우는 사이에 다른 요청이 먼저 지우면 delete(entity) 는 500 으로 터진다.
        if (followRepository.deleteByIdAndFollowerId(followId, me.userId()) == 0) {
            throw notFound(followId);
        }
    }

    /** 팔로잉 목록  */
    @Transactional(readOnly = true)
    public CursorResponse<FollowDto> findFollowings(UUID followerId, CursorRequest page,
            String nameLike) {
        CursorRequest normalized = normalize(page);
        return toResponse(
                viewLoader.loadFollowingsSlice(followerId, normalized, nameLike),
                normalized,
                viewLoader.countFollowings(followerId, nameLike));
    }

    /** 팔로워 목록 */
    @Transactional(readOnly = true)
    public CursorResponse<FollowDto> findFollowers(UUID followeeId, CursorRequest page,
            String nameLike) {
        CursorRequest normalized = normalize(page);
        return toResponse(
                viewLoader.loadFollowersSlice(followeeId, normalized, nameLike),
                normalized,
                viewLoader.countFollowers(followeeId, nameLike));
    }

    /**
     * 프로필 화면의 팔로우 요약.
     *
     * @param me 비로그인이면 {@code null}. {@code followedByMe}·{@code followingMe} -> false
     */
    @Transactional(readOnly = true)
    public FollowSummaryDto findSummary(AuthPrincipal me, UUID userId) {
        // 존재하지 않는 사용자를 팔로워 0 명으로 보여주면 오타를 알아챌 수 없음.
        // 존재만 확인하면 되므로 엔티티를 통째로 읽지 않는다.
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(UserErrorCode.NOT_FOUND)
                    .addDetail("userId", userId.toString());
        }
        return viewLoader.loadSummary(userId, me == null ? null : me.userId());
    }

    /**
     * 스펙에 두 파라미터가 없어 그대로 두면 {@code sortBy} -> Null 처리
     */
    private static CursorRequest normalize(CursorRequest page) {
        return new CursorRequest(page.cursor(), page.idAfter(), page.limit(),
                SORT_BY_CREATED_AT, SortDirection.DESCENDING);
    }

    /**
     * 자르기 · hasNext · 다음 커서는 {@link CursorResponse#of}
     * 응답에 나가지 않는 {@code createdAt} 는 여기서만 제외.
     */
    private static CursorResponse<FollowDto> toResponse(List<FollowRow> fetched,
            CursorRequest page, long totalCount) {
        CursorResponse<FollowRow> paged = CursorResponse.of(
                fetched, page, totalCount, FollowRow::createdAt, row -> row.follow().id());

        return new CursorResponse<>(
                paged.data().stream().map(FollowRow::follow).toList(),
                paged.nextCursor(),
                paged.nextIdAfter(),
                paged.hasNext(),
                paged.totalCount(),
                paged.sortBy(),
                paged.sortDirection());
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.NOT_FOUND)
                        .addDetail("userId", userId.toString()));
    }

    private static BusinessException notFound(UUID followId) {
        return new BusinessException(FollowErrorCode.NOT_FOUND)
                .addDetail("followId", followId.toString());
    }

    private static BusinessException alreadyFollowing(UUID followeeId, Throwable cause) {
        BusinessException e = cause == null
                ? new BusinessException(FollowErrorCode.ALREADY_FOLLOWING)
                : new BusinessException(FollowErrorCode.ALREADY_FOLLOWING, cause);
        return e.addDetail("followeeId", followeeId.toString());
    }
}
