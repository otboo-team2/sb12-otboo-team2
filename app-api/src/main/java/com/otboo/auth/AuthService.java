package com.otboo.auth;

import com.otboo.auth.dto.JwtDto;
import com.otboo.auth.exception.AuthErrorCode;
import com.otboo.common.exception.BusinessException;
import com.otboo.common.security.JwtProvider;
import com.otboo.user.dto.UserDto;
import com.otboo.user.entity.RefreshToken;
import com.otboo.user.entity.User;
import com.otboo.user.repository.RefreshTokenRepository;
import com.otboo.user.repository.UserRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    /** @return 응답 본문과 쿠키에 실을 리프레시 토큰 원문 */
    @Transactional
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
        return issue(user);
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

    private SignInResult issue(User user) {
        Instant now = Instant.now();
        String accessToken = jwtProvider.createAccessToken(user, now);
        String rawRefreshToken = jwtProvider.generateRefreshToken();

        refreshTokenRepository.save(RefreshToken.issue(
                user,
                jwtProvider.hashRefreshToken(rawRefreshToken),
                jwtProvider.refreshTokenExpiry(now)
        ));

        return new SignInResult(new JwtDto(UserDto.from(user), accessToken), rawRefreshToken);
    }

    public record SignInResult(JwtDto jwt, String refreshToken) {
    }
}
