package com.otboo.common.test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * DB 가 필요한 통합 테스트의 부모 클래스. 각 파트는 이걸 상속만 하면 된다.
 *
 * <pre>
 *   class ClothesServiceTest extends IntegrationTestSupport {
 *       {@code @Autowired} ClothesRepository repository;
 *       ...
 *   }
 * </pre>
 *
 * <h2>왜 H2 가 아니라 Testcontainers 인가 (2026-08-29 확정)</h2>
 * RDB 를 MySQL 로 확정했는데 H2 는 방언이 달라서 JSON 함수·{@code ON DUPLICATE KEY} 같은
 * MySQL 전용 구문을 못 쓴다. <b>테스트만 통과하고 운영에서 깨지는</b> 상황이 생긴다.
 * 중급 프로젝트에서 이 항목이 미결로 끝나 Repository 통합테스트가 통째로 보류됐던 자리다.
 *
 * <h2>동작 방식</h2>
 * 컨테이너를 static 으로 한 번만 띄우고 모든 테스트 클래스가 공유한다.
 * {@code @ServiceConnection} 이 datasource URL·계정을 자동으로 주입하므로
 * 테스트에 DB 설정을 따로 쓸 필요가 없다.
 *
 * <p>문자셋·타임존은 {@code docker-compose.yml} 과 같은 값으로 맞춰야 한다.
 * 다르면 테스트에서만 통과하는 시간 버그가 생긴다.
 *
 * <p>⚠️ Docker 가 떠 있어야 한다. CI 에서도 마찬가지다.
 */
@SpringBootTest
public abstract class IntegrationTestSupport {

    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("otboo")
            .withCommand(
                    "--character-set-server=utf8mb4",
                    "--collation-server=utf8mb4_0900_ai_ci",
                    "--default-time-zone=+00:00"
            );

    static {
        // 클래스마다 재기동하지 않도록 한 번만 띄운다. JVM 종료 시 Ryuk 이 정리한다.
        MYSQL.start();
    }
}
