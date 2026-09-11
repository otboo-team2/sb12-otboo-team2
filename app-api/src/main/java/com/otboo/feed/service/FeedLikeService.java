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
 *
 * <h2>카운터 UPDATE 가 먼저, 행 INSERT · DELETE 가 나중</h2>
 * 순서를 바꾸면 데드락이 난다.
 * {@code feed_likes} INSERT 는 FK 검사를 위해 피드 행에 공유(S) 락을 잡음.
 * but 동시에 들어온 두 요청이 모두 S 락을 쥔 채 카운터 UPDATE 의 배타(X) 락을 서로 기다림.
 * 카운터 UPDATE 로 X 락을 먼저 잡으면 같은 피드에 대한 요청이 줄을 서서 교착이 생기지 않는다.
 * 중복 좋아요 · 없는 좋아요 취소로 예외가 나면 트랜잭션 전체가 롤백되므로 먼저 바꾼 카운터도 되돌아간다.
 * ({@code FeedConcurrencyIntegrationTest} B1 · B3 · A1)
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

        // 락을 잡기 전에 거른다. 연타의 대부분은 여기서 끝나 피드 행 락을 기다리지 않는다.
        if (feedLikeRepository.existsByFeedIdAndUserId(feedId, me.userId())) {
            throw new BusinessException(FeedErrorCode.ALREADY_LIKED)
                    .addDetail("feedId", feedId.toString());
        }

        // 피드 행 X 락을 가장 먼저 잡는다. 순서를 바꾸면 데드락
        feedRepository.increaseLikeCount(feedId);
        try {
            feedLikeRepository.saveAndFlush(FeedLike.of(feed, user));
        } catch (DataIntegrityViolationException e) {
            // 확인과 저장 사이에 다른 요청이 먼저 넣었다. 예외로 롤백 -> 올린 카운터로 Back
            throw new BusinessException(FeedErrorCode.ALREADY_LIKED, e)
                    .addDetail("feedId", feedId.toString());
        }

        return viewLoader.loadOne(feedId, me.userId());
    }

    @Transactional
    public void unlike(AuthPrincipal me, UUID feedId) {
        findFeed(feedId);

        // 좋아요와 같은 순서로 피드 행 X 락부터 잡는다. 순서가 다르면 좋아요 · 취소가 섞일 때 교착한다.
        feedRepository.decreaseLikeCount(feedId);

        // 지워진 행 수로 판단한다. 먼저 조회하면 그 사이에 다른 요청이 지워도 알 수 없다.
        // 0 이면 예외로 롤백 -> 내린 카운터도 Back
        if (feedLikeRepository.deleteByFeedIdAndUserId(feedId, me.userId()) == 0) {
            throw new BusinessException(FeedErrorCode.NOT_LIKED)
                    .addDetail("feedId", feedId.toString());
        }
    }

    private Feed findFeed(UUID feedId) {
        return feedRepository.findById(feedId)
                .orElseThrow(() -> new BusinessException(FeedErrorCode.NOT_FOUND)
                        .addDetail("feedId", feedId.toString()));
    }
}
