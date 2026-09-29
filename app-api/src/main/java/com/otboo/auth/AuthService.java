package com.otboo.auth;

import com.otboo.auth.dto.JwtDto;
import com.otboo.auth.exception.AuthErrorCode;
import com.otboo.common.exception.BusinessException;
import com.otboo.common.security.JwtProvider;
import com.otboo.user.dto.UserDto;
import com.otboo.user.entity.RefreshToken;
import com.otboo.user.entity.User;
import com.otboo.user.entity.UserOAuthAccount;
import com.otboo.user.repository.RefreshTokenRepository;
import com.otboo.user.repository.UserOAuthAccountRepository;
import com.otboo.user.repository.UserRepository;
import com.otboo.user.entity.OAuthProvider;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserOAuthAccountRepository oauthAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final TransactionTemplate transactionTemplate;

    /**
     * 비밀번호 검사(BCrypt)는 트랜잭션 밖에서 한다.
     *
     * <p>BCrypt 는 코어 하나를 약 0.1초 쓰는 CPU 작업이다. 트랜잭션 안에 두면 그동안 DB 커넥션을
     * 쥐고 있는데, CPU 가 포화되면 이 시간이 수 초로 늘어나 커넥션 풀이 먼저 바닥난다.
     * 부하테스트(1 vCPU, 풀 20)에서 15 RPS 부터 풀 대기 30초 타임아웃 → 500 이 났다.
     * 조회와 발급만 짧게 트랜잭션으로 감싼다.
     *
     * @return 응답 본문과 쿠키에 실을 리프레시 토큰 원문
     */
    public SignInResult signIn(String email, String rawPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_CREDENTIALS));

        // 사유를 나누지 않는다. "비밀번호가 틀렸다"고 알려주면 가입 여부가 노출된다.
        if (!user.hasPassword() || !passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }
        if (user.isLocked()) {
            throw new BusinessException(AuthErrorCode.ACCOUNT_LOCKED);
        }
        return transactionTemplate.execute(status -> issue(user));
    }

    /**
     * 리프레시 토큰 회전. 쓴 토큰은 즉시 지우고 새로 발급한다.
     *
     * <p>삭제된 행이 0 이면 다른 요청이 이미 같은 토큰을 소비한 것이므로 거절한다.
     * 이 확인이 없으면 동시 재발급 요청 두 개가 모두 성공한다.
     */
    @Transactional
    public SignInResult reissue(String rawRefreshToken) {
        String hash = jwtProvider.hashRefreshToken(rawRefreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.REFRESH_TOKEN_INVALID));

        if (refreshTokenRepository.deleteByTokenHash(hash) == 0) {
            throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }
        if (stored.isExpired(Instant.now())) {
            throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }

        User user = stored.getUser();
        if (user.isLocked()) {
            throw new BusinessException(AuthErrorCode.ACCOUNT_LOCKED);
        }
        return issue(user);
    }

    @Transactional
    public void signOut(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return; // 이미 로그아웃된 상태를 실패로 만들지 않는다
        }
        refreshTokenRepository.deleteByTokenHash(jwtProvider.hashRefreshToken(rawRefreshToken));
    }

    /**
     * 이미 신원이 확인된 사용자에게 토큰을 발급한다. 소셜 로그인이 쓴다.
     *
     * <p>비밀번호 검사를 건너뛰므로 <b>호출 전에 신원 확인이 끝나 있어야 한다.</b>
     * 지금은 {@code OtbooOAuth2UserService} 만 부른다 — 거기서 제공자 인증이 끝난다.
     */
    @Transactional
    public SignInResult issueFor(User user) {
        if (user.isLocked()) {
            throw new BusinessException(AuthErrorCode.ACCOUNT_LOCKED);
        }
        return issue(user);
    }

    private SignInResult issue(User user) {
        Instant now = Instant.now();
        String accessToken = jwtProvider.createAccessToken(user, now);
        String rawRefreshToken = jwtProvider.generateRefreshToken();

        refreshTokenRepository.save(RefreshToken.issue(
                user,
                jwtProvider.hashRefreshToken(rawRefreshToken),
                jwtProvider.refreshTokenExpiry(now)
        ));

        // 프론트가 "연결된 소셜"을 표시한다. 여기서 채우지 않으면 항상 빈 배열이 나간다.
        List<String> linkedProviders = oauthAccountRepository.findAllByUserId(user.getId()).stream()
                .map(UserOAuthAccount::getProvider)
                .map(OAuthProvider::code)
                .toList();

        return new SignInResult(
                new JwtDto(UserDto.from(user, linkedProviders), accessToken), rawRefreshToken);
    }

    public record SignInResult(JwtDto jwt, String refreshToken) {
    }
}
