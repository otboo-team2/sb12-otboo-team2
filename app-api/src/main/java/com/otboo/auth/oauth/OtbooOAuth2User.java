package com.otboo.auth.oauth;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * 인증 단계에서 확정한 <b>우리 쪽 사용자 id</b> 를 성공 핸들러까지 나르는 그릇.
 *
 * <p>이게 없으면 성공 핸들러가 이메일로 사용자를 다시 조회해야 하는데, 그러면
 * 이메일이 없는 경우(카카오)를 못 다루고 조회도 한 번 더 나간다.
 */
public class OtbooOAuth2User implements OAuth2User {

    private final UUID userId;
    private final Map<String, Object> attributes;

    public OtbooOAuth2User(UUID userId, Map<String, Object> attributes) {
        this.userId = userId;
        this.attributes = Map.copyOf(attributes);
    }

    public UUID userId() {
        return userId;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    /**
     * 여기서 주는 권한은 쓰지 않는다. 인가는 우리가 발급한 JWT 의 role 로 판단하고,
     * 이 인증은 토큰을 발급하는 순간까지만 산다.
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String getName() {
        return userId.toString();
    }
}
