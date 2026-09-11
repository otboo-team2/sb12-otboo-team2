package com.otboo.auth.oauth;

import com.otboo.user.entity.OAuthProvider;
import java.util.Map;

/**
 * 제공자마다 제각각인 응답을 우리가 쓰는 네 값으로 줄인 것.
 *
 * <p>제공자가 늘어나면 {@link #of} 에 분기를 하나 더 추가한다. 서비스 로직은 손대지 않는다.
 *
 * @param providerUserId 제공자의 고유 식별자. <b>이메일이 아니다</b> — 이메일은 바뀔 수 있다
 * @param email          없을 수 있다(카카오 선택 동의). 그 처리는 {@link OAuthLoginService} 가 한다
 * @param emailVerified  제공자가 이메일 소유를 보증하는가. 기존 계정 연결 허용 여부를 가른다
 * @param displayName    표시 이름. 없으면 이메일 앞부분을 쓴다
 */
public record OAuthAttributes(
        String providerUserId,
        String email,
        boolean emailVerified,
        String displayName
) {

    public static OAuthAttributes of(OAuthProvider provider, Map<String, Object> attributes) {
        return switch (provider) {
            case GOOGLE -> google(attributes);
            case KAKAO -> kakao(attributes);
        };
    }

    /** 구글은 평평한 구조로 준다. {@code sub} 가 고유 식별자다. */
    private static OAuthAttributes google(Map<String, Object> attributes) {
        String email = string(attributes.get("email"));
        return new OAuthAttributes(
                string(attributes.get("sub")),
                email,
                Boolean.TRUE.equals(attributes.get("email_verified")),
                displayName(string(attributes.get("name")), email));
    }

    /**
     * 카카오는 {@code kakao_account} 안에 중첩해서 준다.
     * 이메일 동의를 안 하면 키 자체가 없고, 동의했어도 미인증 상태일 수 있다.
     */
    @SuppressWarnings("unchecked")
    private static OAuthAttributes kakao(Map<String, Object> attributes) {
        Map<String, Object> account =
                (Map<String, Object>) attributes.getOrDefault("kakao_account", Map.of());
        Map<String, Object> profile =
                (Map<String, Object>) account.getOrDefault("profile", Map.of());

        String email = string(account.get("email"));
        return new OAuthAttributes(
                string(attributes.get("id")),
                email,
                Boolean.TRUE.equals(account.get("is_email_verified")),
                displayName(string(profile.get("nickname")), email));
    }

    private static String displayName(String given, String email) {
        if (given != null && !given.isBlank()) {
            // users.name 이 VARCHAR(50) 이다. 긴 이름이 오면 insert 가 실패한다.
            return given.length() > 50 ? given.substring(0, 50) : given;
        }
        if (email != null && email.contains("@")) {
            return email.substring(0, Math.min(50, email.indexOf('@')));
        }
        return "사용자";
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
