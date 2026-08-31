package com.otboo;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.common.test.IntegrationTestSupport;
import java.time.ZoneId;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 스캐폴딩이 실제로 동작하는지 확인하는 스모크 테스트.
 * 통합 테스트를 어떻게 쓰는지 보여주는 예시이기도 하다.
 */
class OtbooApiApplicationTests extends IntegrationTestSupport {

    @Autowired
    DataSource dataSource;

    @Test
    @DisplayName("애플리케이션 컨텍스트가 뜨고 DB 에 붙는다")
    void contextLoads() {
        assertThat(dataSource).isNotNull();
    }

    @Test
    @DisplayName("DB 문자셋이 utf8mb4 라 이모지를 저장할 수 있다")
    void charsetIsUtf8mb4() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String charset = jdbc.queryForObject("SELECT @@character_set_server", String.class);
        assertThat(charset).isEqualTo("utf8mb4");
    }

    @Test
    @DisplayName("DB 와 JVM 타임존이 모두 UTC 다")
    void timezoneIsUtc() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String dbTimeZone = jdbc.queryForObject("SELECT @@global.time_zone", String.class);

        assertThat(dbTimeZone).isEqualTo("+00:00");
        assertThat(ZoneId.systemDefault().getId()).isEqualTo("UTC");
    }
}
