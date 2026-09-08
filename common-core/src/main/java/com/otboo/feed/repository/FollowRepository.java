package com.otboo.feed.repository;

import com.otboo.feed.entity.Follow;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FollowRepository extends JpaRepository<Follow, UUID> {

    /**
     * 저장 전 빠른 실패용.
     * 이 확인과 저장 사이에 다른 요청이 먼저 넣을 수 있다.
     */
    boolean existsByFollowerIdAndFolloweeId(UUID followerId, UUID followeeId);
}
