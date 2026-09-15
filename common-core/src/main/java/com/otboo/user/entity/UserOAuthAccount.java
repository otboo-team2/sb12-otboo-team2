package com.otboo.user.entity;

import com.otboo.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 계정에 연결된 소셜 로그인.
 *
 * <p>한 사용자가 구글과 카카오를 모두 연결할 수 있어 {@code users} 와 1:N 이다.
 * {@code UNIQUE (provider, provider_user_id)} 가 <b>같은 소셜 계정이 두 사람에게 붙는 것</b>을 막는다.
 */
@Entity
@Getter
@Table(name = "user_oauth_accounts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserOAuthAccount extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OAuthProvider provider;

    /** 제공자가 준 고유 식별자. 이메일이 아니다 — 이메일은 바뀔 수 있다. */
    @Column(name = "provider_user_id", nullable = false, length = 191, updatable = false)
    private String providerUserId;

    private UserOAuthAccount(User user, OAuthProvider provider, String providerUserId) {
        this.user = user;
        this.provider = provider;
        this.providerUserId = providerUserId;
    }

    public static UserOAuthAccount link(User user, OAuthProvider provider, String providerUserId) {
        return new UserOAuthAccount(user, provider, providerUserId);
    }
}
