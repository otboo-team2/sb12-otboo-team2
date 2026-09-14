package com.otboo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 비밀번호 인코더를 {@code SecurityConfig} 밖에 둔다.
 *
 * <p>안에 두면 순환 참조가 생긴다. 인코더가 필요한 {@code AuthService} 가
 * {@code SecurityConfig} 에 의존하게 되는데, {@code SecurityConfig} 는 소셜 로그인
 * 성공 핸들러를 필요로 하고 그 핸들러가 다시 {@code AuthService} 를 쓴다.
 *
 * <p>애초에 비밀번호 해싱은 웹 보안 설정이 아니라 독립적인 관심사다.
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
