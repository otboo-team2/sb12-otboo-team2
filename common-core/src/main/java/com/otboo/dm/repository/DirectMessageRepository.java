package com.otboo.dm.repository;

import com.otboo.dm.entity.DirectMessage;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DirectMessageRepository extends JpaRepository<DirectMessage, UUID> {

    long countByDmKey(String dmKey);

    @Query("SELECT COUNT(DISTINCT dm.dmKey) FROM DirectMessage dm WHERE dm.sender.id = :userId OR dm.receiver.id = :userId")
    long countConversationPartners(@Param("userId") UUID userId);
}
