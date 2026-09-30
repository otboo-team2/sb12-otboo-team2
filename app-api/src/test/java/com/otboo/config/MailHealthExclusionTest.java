package com.otboo.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.common.test.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthContributorRegistry;
import org.springframework.test.context.TestPropertySource;

/**
 * 메일 서버 상태가 ALB 헬스체크를 좌우하면 안 된다.
 * 호스트가 설정돼 있어도 health 에 메일 검사가 들어가지 않아야 한다.
 */
@TestPropertySource(properties = {"spring.mail.host=localhost", "spring.mail.port=1"})
class MailHealthExclusionTest extends IntegrationTestSupport {

    @Autowired HealthContributorRegistry registry;

    @Test
    @DisplayName("메일 호스트가 있어도 health 에 mail 검사가 등록되지 않는다")
    void mailIsNotAHealthContributor() {
        assertThat(registry.getContributor("mail")).isNull();
        assertThat(registry.getContributor("db")).isNotNull(); // 레지스트리 자체는 살아 있다
    }
}
