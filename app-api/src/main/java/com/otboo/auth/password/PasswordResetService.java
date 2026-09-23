package com.otboo.auth.password;

import com.otboo.auth.dto.ResetPasswordRequest;
import com.otboo.user.entity.OAuthProvider;
import com.otboo.user.entity.User;
import com.otboo.user.entity.UserOAuthAccount;
import com.otboo.user.repository.RefreshTokenRepository;
import com.otboo.user.repository.UserOAuthAccountRepository;
import com.otboo.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호 초기화.
 *
 * <h2>어떤 경우에도 결과를 밖으로 알리지 않는다</h2>
 * 계정이 없든, 소셜 전용이든, 쿨다운에 걸렸든 응답은 똑같이 204 다.
 * 응답이 갈리는 순간 이 API 는 "이 이메일이 가입돼 있는지" 물어보는 도구가 된다.
 * 자세한 이유는 {@link ResetPasswordRequest} 에 적어두었다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final String COOLDOWN_KEY_PREFIX = "password-reset:cooldown:";

    private final UserRepository userRepository;
    private final UserOAuthAccountRepository oauthAccountRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetMailer mailer;
    private final PasswordResetProperties properties;
    private final StringRedisTemplate redis;

    /**
     * 메일 발송을 트랜잭션 <b>안에서</b> 한다. 커밋한 뒤에 보내면 발송이 실패했을 때
     * 아무도 모르는 비밀번호만 남아 사용자가 자기 계정에서 잠긴다. 발송이 실패하면
     * 비밀번호 변경도 함께 되돌아가야 한다.
     *
     * <p>대신 발송이 늦어지면 DB 커넥션을 붙잡는다. {@code spring.mail.properties} 의
     * 타임아웃(5초)이 그 상한이다.
     */
    @Transactional
    public void reset(ResetPasswordRequest request) {
        String email = request.email();
        if (!startCooldown(email)) {
            log.info("비밀번호 초기화 요청이 쿨다운에 걸려 넘어갔다");
            return;
        }
        try {
            userRepository.findByEmail(email).ifPresent(this::resetFor);
        } catch (RuntimeException e) {
            // 실패한 시도가 쿨다운을 차지하면 사용자는 아무 안내도 못 받은 채 기다려야 한다.
            clearCooldown(email);
            throw e;
        }
    }

    private void resetFor(User user) {
        if (!user.hasPassword()) {
            mailer.send(PasswordResetMail.socialLoginNotice(user.getEmail(), providersOf(user)));
            return;
        }

        String temporaryPassword = TemporaryPassword.generate();
        user.changePassword(passwordEncoder.encode(temporaryPassword));
        // 비밀번호를 바꾸는 이유 중 하나가 "누가 내 계정을 쓰는 것 같다"인데
        // 남의 세션이 살아 있으면 소용이 없다. UserService.changePassword 와 같은 처리다.
        refreshTokenRepository.deleteAllByUser(user);

        mailer.send(PasswordResetMail.temporaryPassword(user.getEmail(), temporaryPassword));
    }

    private List<OAuthProvider> providersOf(User user) {
        return oauthAccountRepository.findAllByUserId(user.getId()).stream()
                .map(UserOAuthAccount::getProvider)
                .toList();
    }

    /**
     * 쿨다운을 선점한다. 이미 누가 차지하고 있으면 {@code false}.
     *
     * <p>Redis 가 죽었다고 비밀번호 초기화까지 막을 이유는 없다. 그때는 통과시킨다 —
     * 남는 위험은 "메일이 여러 통 갈 수 있다"이고, 막았을 때의 손해는 "로그인을 못 한다"다.
     */
    private boolean startCooldown(String email) {
        try {
            return Boolean.TRUE.equals(redis.opsForValue()
                    .setIfAbsent(COOLDOWN_KEY_PREFIX + email, "1", properties.cooldown()));
        } catch (RuntimeException e) {
            log.warn("비밀번호 초기화 쿨다운을 확인하지 못했다. 요청을 그대로 진행한다.", e);
            return true;
        }
    }

    private void clearCooldown(String email) {
        try {
            redis.delete(COOLDOWN_KEY_PREFIX + email);
        } catch (RuntimeException e) {
            log.warn("비밀번호 초기화 쿨다운을 해제하지 못했다. 만료까지 재요청이 막힌다.", e);
        }
    }
}
