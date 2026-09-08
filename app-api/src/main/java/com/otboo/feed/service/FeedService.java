package com.otboo.feed.service;

import com.otboo.common.exception.BusinessException;
import com.otboo.common.pagination.CursorResponse;
import com.otboo.common.security.AuthPrincipal;
import com.otboo.feed.dto.FeedCreateRequest;
import com.otboo.feed.dto.FeedDto;
import com.otboo.feed.dto.FeedSearchCondition;
import com.otboo.feed.dto.FeedUpdateRequest;
import com.otboo.feed.entity.Feed;
import com.otboo.feed.exception.FeedErrorCode;
import com.otboo.feed.query.FeedReferenceQuery;
import com.otboo.feed.query.FeedViewLoader;
import com.otboo.feed.repository.FeedRepository;
import com.otboo.feed.search.FeedSearchPort;
import com.otboo.user.entity.User;
import com.otboo.user.exception.UserErrorCode;
import com.otboo.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 피드 등록 · 수정 · 삭제 · 목록 조회.
 *
 * <h2>응답은 항상 {@link FeedViewLoader} 를 거친다</h2>
 * 등록 직후 {@code FeedDto} 를 엔티티에서 직접 만들면 작성자 프로필 이미지·날씨·의상 속성이
 * 빠지거나 다른 모양으로 나간다. 목록과 단건의 응답이 조금씩 다르면 프론트에 조건 분기가 생긴다.
 * 만드는 경로를 하나로 고정한다.
 */
@Service
@RequiredArgsConstructor
public class FeedService {

    private final FeedRepository feedRepository;
    private final UserRepository userRepository;
    private final FeedSearchPort feedSearch;
    private final FeedViewLoader viewLoader;
    private final FeedReferenceQuery referenceQuery;

    @Transactional
    public FeedDto create(AuthPrincipal me, FeedCreateRequest request) {
        User author = userRepository.findById(me.userId())
                .orElseThrow(() -> new BusinessException(UserErrorCode.NOT_FOUND));

        if (!referenceQuery.weatherExists(request.weatherId())) {
            throw new BusinessException(FeedErrorCode.WEATHER_NOT_FOUND)
                    .addDetail("weatherId", request.weatherId().toString());
        }
        List<UUID> clothesIds = distinct(request.clothesIdsOrEmpty());
        validateClothes(me.userId(), clothesIds);

        // 응답을 조회 전용 SQL 로 다시 읽으므로 flush 가 먼저다.
        // 안 그러면 아직 INSERT 되지 않은 행을 찾지 못해 null 이 나간다.
        Feed feed = feedRepository.saveAndFlush(
                Feed.create(author, request.weatherId(), request.content(), clothesIds));

        return viewLoader.loadOne(feed.getId(), me.userId());
    }

    @Transactional
    public FeedDto update(AuthPrincipal me, UUID feedId, FeedUpdateRequest request) {
        Feed feed = findEditableFeed(me, feedId);
        feed.updateContent(request.content());
        feedRepository.flush();

        return viewLoader.loadOne(feedId, me.userId());
    }

    @Transactional
    public void delete(AuthPrincipal me, UUID feedId) {
        Feed feed = findDeletableFeed(me, feedId);
        // feed_clothes · feed_likes · comments 는 DB 의 ON DELETE CASCADE 로 함께 지워진다.
        feedRepository.delete(feed);
    }

    /**
     * 목록 조회. 검색 엔진이 id 순서를 정하고, 본문은 MySQL 에서 읽는다.
     *
     * @param viewerId 비로그인 조회면 {@code null} — {@code likedByMe} 가 전부 false 가 된다
     */
    @Transactional(readOnly = true)
    public CursorResponse<FeedDto> search(FeedSearchCondition condition, UUID viewerId) {
        FeedSearchPort.FeedSearchResult result = feedSearch.search(condition);
        if (result.feedIds().isEmpty()) {
            return CursorResponse.empty(condition.page());
        }

        List<FeedDto> feeds = viewLoader.load(result.feedIds(), viewerId);
        Function<FeedDto, Object> sortKey = condition.sortsByLikeCount()
                ? FeedDto::likeCount
                : FeedDto::createdAt;

        return CursorResponse.of(feeds, condition.page(), result.totalCount(), sortKey, FeedDto::id);
    }

    /** 수정은 작성자 본인만 할 수 있다. 관리자도 남의 글 내용을 고칠 수는 없다. */
    private Feed findEditableFeed(AuthPrincipal me, UUID feedId) {
        Feed feed = findFeedWithAuthor(feedId);
        if (!feed.isAuthor(me.userId())) {
            throw new BusinessException(FeedErrorCode.NOT_AUTHOR)
                    .addDetail("feedId", feedId.toString());
        }
        return feed;
    }

    /** 삭제는 관리자도 할 수 있어야 신고 처리가 된다. */
    private Feed findDeletableFeed(AuthPrincipal me, UUID feedId) {
        Feed feed = findFeedWithAuthor(feedId);
        if (!feed.isAuthor(me.userId()) && !me.isAdmin()) {
            throw new BusinessException(FeedErrorCode.NOT_AUTHOR)
                    .addDetail("feedId", feedId.toString());
        }
        return feed;
    }

    private Feed findFeedWithAuthor(UUID feedId) {
        return feedRepository.findWithAuthorById(feedId)
                .orElseThrow(() -> new BusinessException(FeedErrorCode.NOT_FOUND)
                        .addDetail("feedId", feedId.toString()));
    }

    /**
     * 같은 옷을 두 번 담아 보내면 {@code uk_feed_clothes} 위반으로 500 이 난다.
     * 사용자 실수를 오류로 만들 이유가 없어 순서를 지키며 중복만 걷어낸다.
     */
    private List<UUID> distinct(List<UUID> clothesIds) {
        return new ArrayList<>(new LinkedHashSet<>(clothesIds));
    }

    private void validateClothes(UUID ownerId, List<UUID> clothesIds) {
        if (clothesIds.isEmpty()) {
            return;
        }
        Map<UUID, UUID> owners = referenceQuery.findClothesOwners(clothesIds);
        for (UUID clothesId : clothesIds) {
            UUID owner = owners.get(clothesId);
            if (owner == null) {
                throw new BusinessException(FeedErrorCode.CLOTHES_NOT_FOUND)
                        .addDetail("clothesId", clothesId.toString());
            }
            if (!owner.equals(ownerId)) {
                throw new BusinessException(FeedErrorCode.CLOTHES_NOT_OWNED)
                        .addDetail("clothesId", clothesId.toString());
            }
        }
    }
}
