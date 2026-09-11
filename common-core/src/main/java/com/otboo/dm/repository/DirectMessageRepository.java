package com.otboo.dm.repository;

import com.otboo.dm.entity.DirectMessage;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DirectMessageRepository extends JpaRepository<DirectMessage, UUID> {

    long countByDmKey(String dmKey);
}
