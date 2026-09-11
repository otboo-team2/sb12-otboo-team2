package com.otboo.auth.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/**
 * 소셜 로그인 실패를 로그인 화면으로 돌려보낸다.
 *
 * <p>프론트({@code LoginForm.tsx})가 {@code ?error} · {@code ?error_message} 를 읽어
 * 그대로 띄우게 되어 있다. 그 계약에 맞춘다.
 */
@Slf4j
@Component
public class OAuth2FailureHandler implements AuthenticationFailureHandler {

    private static final String DEFAULT_MESSAGE = "소셜 로그인에 실패했습니다. 다시 시도해주세요.";

    @Value("${otboo.oauth.failure-redirect:/auth/login}")
    private String failureRedirect;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        String code = "oauth_failed";
        String message = DEFAULT_MESSAGE;

        if (exception instanceof OAuth2AuthenticationException oauthException) {
            var error = oauthException.getError();
            if (error.getErrorCode() != null) {
                code = error.getErrorCode();
            }
            if (error.getDescription() != null && !error.getDescription().isBlank()) {
                message = error.getDescription();
            }
        }

        // 원인은 로그에만 남긴다. 화면에는 우리가 정한 문구만 나간다.
        log.warn("oauth_login_failed code={}", code, exception);

        response.sendRedirect("%s?error=%s&error_message=%s".formatted(
                failureRedirect, encode(code), encode(message)));
    }

    /** 인코딩하지 않으면 메시지의 & 나 개행이 다른 파라미터를 만들어낸다. */
    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
