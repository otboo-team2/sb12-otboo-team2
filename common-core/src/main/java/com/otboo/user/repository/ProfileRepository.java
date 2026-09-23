package com.otboo.user.repository;

import com.otboo.user.entity.Profile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileRepository extends JpaRepository<Profile, UUID> {

    /** 응답에 계정 이름과 지명이 모두 필요하므로 한 번에 가져온다. 안 그러면 쿼리가 3번 나간다. */
    @EntityGraph(attributePaths = {"user", "region"})
    Optional<Profile> findByUserId(UUID userId);
}
