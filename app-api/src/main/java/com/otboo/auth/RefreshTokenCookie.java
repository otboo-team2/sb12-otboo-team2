package com.otboo.auth;

import com.otboo.common.security.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 리프레시 토큰 쿠키를 만드는 곳. 일반 로그인과 소셜 로그인이 <b>같은 쿠키</b>를 심어야
 * 프론트의 {@code POST /api/auth/refresh} 한 경로로 세션이 복구된다.
 *
 * <p>속성이 두 군데로 갈라지면 한쪽만 고쳐서 "소셜 로그인만 새로고침하면 풀린다" 같은
 * 증상이 난다. 그래서 한 곳에 둔다.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenCookie {

    public static final String NAME = "REFRESH_TOKEN";

    private final JwtProperties jwtProperties;

    public ResponseCookie issue(String value) {
        return base(value)
                .maxAge(jwtProperties.refreshExpiry())
                .build();
    }

    public ResponseCookie expired() {
        return base("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)                       // JS 가 못 읽는다. XSS 로 탈취되지 않는다.
                .secure(jwtProperties.cookieSecure())
                // ⚠️ Strict 로 바꾸면 안 된다. 소셜 로그인은 제공자 도메인에서 우리 쪽으로
                //    돌아오는 크로스사이트 이동이라, Strict 면 그 요청에 쿠키가 실리지 않는다.
                .sameSite("Lax")
                .path("/");
    }
}
