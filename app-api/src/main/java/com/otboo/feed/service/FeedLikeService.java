package com.otboo.feed.service;

import com.otboo.common.exception.BusinessException;
import com.otboo.common.security.AuthPrincipal;
import com.otboo.feed.dto.FeedDto;
import com.otboo.feed.entity.Feed;
import com.otboo.feed.entity.FeedLike;
import com.otboo.feed.exception.FeedErrorCode;
import com.otboo.feed.query.FeedViewLoader;
import com.otboo.feed.repository.FeedLikeRepository;
import com.otboo.feed.repository.FeedRepository;
import com.otboo.user.entity.User;
import com.otboo.user.exception.UserErrorCode;
import com.otboo.user.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 피드 좋아요 · 좋아요 취소.
 *
 * <h2>동시성</h2>
 * "조회해서 없으면 저장" 은 같은 사용자가 버튼을 두 번 빠르게 누르면 두 요청 모두
 * "없음" 을 보고 통과한다. 그러면 {@code like_count} 만 2 가 되고 좋아요 행은 유니크 제약에
 * 걸려 하나만 남는다
 *
 * <p>그래서 미리 확인은 <b>빠른 실패용</b>일 뿐이고, 진짜 방어는 유니크 제약 위반을 잡는 쪽이다.
 * 카운터 증감도 {@code UPDATE ... SET like_count = like_count + 1} 로 DB 에 맡긴다.
 */
@Service
@RequiredArgsConstructor
public class FeedLikeService {

    private final FeedRepository feedRepository;
    private final FeedLikeRepository feedLikeRepository;
    private final UserRepository userRepository;
    private final FeedViewLoader viewLoader;

    @Transactional
    public FeedDto like(AuthPrincipal me, UUID feedId) {
        Feed feed = findFeed(feedId);
        User user = userRepository.findById(me.userId())
                .orElseThrow(() -> new BusinessException(UserErrorCode.NOT_FOUND));

        if (feedLikeRepository.existsByFeedIdAndUserId(feedId, me.userId())) {
            throw new BusinessException(FeedErrorCode.ALREADY_LIKED)
                    .addDetail("feedId", feedId.toString());
        }
        try {
            feedLikeRepository.saveAndFlush(FeedLike.of(feed, user));
        } catch (DataIntegrityViolationException e) {
            // 확인과 저장 사이에 다른 요청이 먼저 넣었다. 카운터는 올리지 않는다.
            throw new BusinessException(FeedErrorCode.ALREADY_LIKED, e)
                    .addDetail("feedId", feedId.toString());
        }
        feedRepository.increaseLikeCount(feedId);

        return viewLoader.loadOne(feedId, me.userId());
    }

    @Transactional
    public void unlike(AuthPrincipal me, UUID feedId) {
        findFeed(feedId);

        // 지워진 행 수로 판단한다. 먼저 조회하면 그 사이에 다른 요청이 지워도 알 수 없다.
        if (feedLikeRepository.deleteByFeedIdAndUserId(feedId, me.userId()) == 0) {
            throw new BusinessException(FeedErrorCode.NOT_LIKED)
                    .addDetail("feedId", feedId.toString());
        }
        feedRepository.decreaseLikeCount(feedId);
    }

    private Feed findFeed(UUID feedId) {
        return feedRepository.findById(feedId)
                .orElseThrow(() -> new BusinessException(FeedErrorCode.NOT_FOUND)
                        .addDetail("feedId", feedId.toString()));
    }
}
