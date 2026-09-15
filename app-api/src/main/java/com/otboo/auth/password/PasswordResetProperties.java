package com.otboo.auth.password;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 비밀번호 초기화 설정.
 *
 * @param from     보내는 사람 주소
 * @param cooldown 같은 주소로 다시 요청할 수 있게 되기까지의 시간. 이게 없으면 남의 메일함을
 *                 초기화 메일로 채울 수 있다(요청에 로그인이 필요 없는 API 다)
 */
@ConfigurationProperties(prefix = "otboo.password-reset")
public record PasswordResetProperties(String from, Duration cooldown) {

    public PasswordResetProperties {
        from = from == null ? "no-reply@otboo.com" : from;
        cooldown = cooldown == null ? Duration.ofMinutes(3) : cooldown;
    }
}
