package com.otboo.user.entity;

/**
 * 소셜 로그인 제공자.
 *
 * <p><b>DB 와 API 의 표기가 다르다.</b> {@code user_oauth_accounts.provider} 의 CHECK 제약은
 * 대문자({@code 'GOOGLE','KAKAO'})이고, Swagger 의 {@code linkedOAuthProviders} 와
 * 프론트가 보내는 경로({@code /oauth2/authorization/google})는 소문자다.
 * 변환을 한 곳에 모아둔다 — 흩어지면 한쪽만 고쳐서 조용히 안 맞는다.
 */
public enum OAuthProvider {

    GOOGLE,
    KAKAO;

    /** 스프링 시큐리티의 registrationId 와 API 응답에 쓰는 표기. */
    public String code() {
        return name().toLowerCase();
    }

    /** {@code /oauth2/authorization/{registrationId}} 의 registrationId 로 찾는다. */
    public static OAuthProvider from(String registrationId) {
        for (OAuthProvider provider : values()) {
            if (provider.code().equalsIgnoreCase(registrationId)) {
                return provider;
            }
        }
        throw new IllegalArgumentException("지원하지 않는 소셜 로그인입니다: " + registrationId);
    }
}
