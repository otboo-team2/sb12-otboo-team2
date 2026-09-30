package com.otboo.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.otboo.common.test.IntegrationTestSupport;
import com.otboo.user.entity.User;
import com.otboo.user.repository.RefreshTokenRepository;
import com.otboo.user.repository.UserRepository;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 로그인 부하테스트에서 BCrypt 가 커넥션을 쥔 채 CPU 를 기다려 풀이 고갈됐다.
 * 비밀번호 검사 중에는 트랜잭션도 커넥션도 없어야 한다.
 */
class SignInTransactionBoundaryTest extends IntegrationTestSupport {

    private static final String PASSWORD = "Passw0rd!";

    @Autowired AuthService authService;
    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired DataSource dataSource;
    @MockitoSpyBean PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.save(User.create("me@otboo.com", passwordEncoder.encode(PASSWORD), "여운정"));
    }

    @Test
    @DisplayName("비밀번호 검사는 트랜잭션 밖에서 하고, 토큰 발급은 저장된다")
    void passwordCheckRunsWithoutConnection() {
        AtomicBoolean inTransaction = new AtomicBoolean(true);
        AtomicBoolean holdsConnection = new AtomicBoolean(true);
        doAnswer(invocation -> {
            inTransaction.set(TransactionSynchronizationManager.isActualTransactionActive());
            holdsConnection.set(TransactionSynchronizationManager.hasResource(dataSource));
            return invocation.callRealMethod();
        }).when(passwordEncoder).matches(any(), any());

        AuthService.SignInResult result = authService.signIn("me@otboo.com", PASSWORD);

        assertThat(inTransaction).isFalse();
        assertThat(holdsConnection).isFalse();
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(refreshTokenRepository.count()).isEqualTo(1);
    }
}
