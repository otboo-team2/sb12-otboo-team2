package com.otboo.notification.repository;

import com.otboo.notification.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    @Query("""
        select n from Notification n
        where n.receiver.id = :receiverId
          and (:cursor is null
               or n.createdAt < :cursor
               or (n.createdAt = :cursor and n.id < :idAfter))
        order by n.createdAt desc, n.id desc
        """)
    List<Notification> findByReceiverId(@Param("receiverId") UUID receiverId,
                                        @Param("cursor") Instant cursor,
                                        @Param("idAfter") UUID idAfter,
                                        Pageable pageable);

    long countByReceiverId(UUID receiverId);

    int deleteByIdAndReceiverId(UUID id, UUID receiverId);
}
