package com.otboo.user.repository;

import com.otboo.user.entity.User;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

/**
 * {@code JpaSpecificationExecutor} 를 쓰는 이유 — 계정 목록은 필터(email/role/locked) 3개와
 * 정렬 2가지, 방향 2가지가 곱해져 조건 조합이 여러 개다. JPQL 로 쓰면 쿼리가 4개로 늘어나고
 * 커서 조건까지 붙어 읽기 어려워진다. Specification 은 조건을 조립해서 하나로 만든다.
 */
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * 전체 사용자 대상 알림(예: 새 의상 속성 추가) 발송을 위해 notification 도메인에서 추가했다.
     */
    @Query("select u.id from User u order by u.id")
    Slice<UUID> findAllIds(Pageable pageable);
}
