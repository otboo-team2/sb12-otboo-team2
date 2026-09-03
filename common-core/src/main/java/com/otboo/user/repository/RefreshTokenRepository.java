package com.otboo.user.repository;

import com.otboo.user.entity.RefreshToken;
import com.otboo.user.entity.User;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * 재발급 시 기존 토큰을 지우고 새로 발급한다(회전).
     * 삭제된 행 수를 돌려주므로, 0 이면 <b>이미 다른 요청이 같은 토큰을 써버린 것</b>이다.
     * 이 값을 확인해야 동시 재발급 경쟁에서 두 개가 다 성공하는 일이 없다.
     */
    @Modifying
    @Query("delete from RefreshToken t where t.tokenHash = :tokenHash")
    int deleteByTokenHash(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("delete from RefreshToken t where t.user = :user")
    void deleteAllByUser(@Param("user") User user);

    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
