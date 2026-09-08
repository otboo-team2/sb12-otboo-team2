package com.otboo.feed.repository;

import com.otboo.feed.entity.FeedLike;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedLikeRepository extends JpaRepository<FeedLike, UUID> {

    boolean existsByFeedIdAndUserId(UUID feedId, UUID userId);

    /** @return 실제로 지워진 행 수. 0 이면 애초에 누르지 않은 좋아요다. */
    long deleteByFeedIdAndUserId(UUID feedId, UUID userId);
}
