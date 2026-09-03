package com.otboo.common.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param secret         HS256 서명 키. 최소 32바이트여야 한다. 운영에서는 환경변수로 주입한다.
 * @param accessExpiry   액세스 토큰 수명. 탈취 피해를 줄이려 짧게 둔다.
 * @param refreshExpiry  리프레시 토큰 수명
 * @param cookieSecure   운영에서는 true. HTTPS 에서만 쿠키가 전송된다.
 */
@ConfigurationProperties(prefix = "otboo.jwt")
public record JwtProperties(
        String secret,
        Duration accessExpiry,
        Duration refreshExpiry,
        boolean cookieSecure
) {

    public JwtProperties {
        if (accessExpiry == null) {
            accessExpiry = Duration.ofMinutes(30);
        }
        if (refreshExpiry == null) {
            refreshExpiry = Duration.ofDays(14);
        }
    }
}
