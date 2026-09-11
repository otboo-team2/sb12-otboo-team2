package com.otboo.feed.repository;

import com.otboo.feed.entity.FeedLike;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeedLikeRepository extends JpaRepository<FeedLike, UUID> {

    boolean existsByFeedIdAndUserId(UUID feedId, UUID userId);

    /**
     * 좋아요 취소.
     * <p><b>{@code @Query} 를 지우고 파생 쿼리 x.
     * 파생 삭제 쿼리 {@code deleteByXxx})는 한 문장 DELETE X, SELECT 로 읽은 뒤 행마다 {@code em.remove} 한다.
     * 읽고 지우는 사이에 다른 요청이 먼저 지우면 Hibernate 가 {@code ObjectOptimisticLockingFailureException} -> 500. 취소 버튼 연타로 재현.
     * ({@code FeedConcurrencyIntegrationTest} A2)
     * @return 실제로 지워진 행 수. 0 이면 애초에 누르지 않았거나 그 사이 이미 취소됐다.
     */
    @Modifying
    @Query("delete from FeedLike l where l.feed.id = :feedId and l.user.id = :userId")
    long deleteByFeedIdAndUserId(@Param("feedId") UUID feedId, @Param("userId") UUID userId);
}
