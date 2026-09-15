package com.otboo.user.repository;

import com.otboo.user.entity.OAuthProvider;
import com.otboo.user.entity.UserOAuthAccount;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserOAuthAccountRepository extends JpaRepository<UserOAuthAccount, UUID> {

    /** 로그인 직후 사용자까지 써야 하므로 같이 가져온다. 안 그러면 쿼리가 두 번 나간다. */
    @EntityGraph(attributePaths = "user")
    Optional<UserOAuthAccount> findByProviderAndProviderUserId(
            OAuthProvider provider, String providerUserId);

    List<UserOAuthAccount> findAllByUserId(UUID userId);
}
