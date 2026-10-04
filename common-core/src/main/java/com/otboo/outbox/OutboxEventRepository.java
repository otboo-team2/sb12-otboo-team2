package com.otboo.outbox;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /** 미발행 이벤트를 오래된 순으로 잠그며 가져온다. 다른 스레드·인스턴스가 잡은 행은 건너뛴다. */
    @Query(value = """
            SELECT * FROM outbox_events
            WHERE published_at IS NULL AND failed_at IS NULL
            ORDER BY created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> findUnpublishedForUpdate(@Param("limit") int limit);

    @Modifying
    @Query(value = "DELETE FROM outbox_events WHERE published_at < :before LIMIT :limit", nativeQuery = true)
    int deletePublishedBefore(@Param("before") Instant before, @Param("limit") int limit);
}
