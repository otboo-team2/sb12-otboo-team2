package com.otboo.auth.oauth;

import com.otboo.auth.AuthService;
import com.otboo.auth.RefreshTokenCookie;
import com.otboo.user.entity.User;
import com.otboo.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * 소셜 인증이 끝난 뒤 <b>우리 리프레시 토큰 쿠키</b>를 심고 SPA 로 돌려보낸다.
 *
 * <p>액세스 토큰은 URL 에 싣지 않는다. 쿼리 스트링은 브라우저 기록·리퍼러·서버 접근 로그에
 * 그대로 남는다. 대신 쿠키만 심고 보내면, 프론트가 부팅하면서 이미 쓰고 있는
 * {@code POST /api/auth/refresh} 로 액세스 토큰을 받아간다({@code useAuthStore}).
 * <b>그래서 프론트에 새로 만들 것이 없다.</b>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final RefreshTokenCookie refreshTokenCookie;

    /**
     * 고정값이다. 요청 파라미터로 받지 않는다 —
     * 받으면 {@code ?redirect=https://공격자} 로 오픈 리다이렉트가 된다.
     */
    @Value("${otboo.oauth.success-redirect:/}")
    private String successRedirect;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException {
        OtbooOAuth2User principal = (OtbooOAuth2User) authentication.getPrincipal();
        User user = userRepository.findById(principal.userId()).orElseThrow();

        AuthService.SignInResult result = authService.issueFor(user);
        response.addHeader(HttpHeaders.SET_COOKIE,
                refreshTokenCookie.issue(result.refreshToken()).toString());

        log.info("oauth_login_success userId={}", user.getId());
        response.sendRedirect(successRedirect);
    }
}
