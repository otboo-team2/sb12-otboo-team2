package com.otboo.pinterest;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * {@code weatherCollectionJob} 이 도는 실행에 {@code pinterestSyncJob} 을 이어서 돌린다.
 *
 * <h2>왜 필요한가</h2>
 * 배포 환경의 스케줄은 {@code --spring.batch.job.name=weatherCollectionJob} 으로만 배치를 띄운다.
 * {@link PinterestSyncJobRunnerConfig} 는 잡 이름이 {@code pinterestSyncJob} 일 때만 켜지므로,
 * 스케줄을 따로 만들지 않으면 배포 환경에서는 핀 동기화가 한 번도 돌지 않는다. 스케줄을 늘리는 대신
 * 이미 도는 실행에 얹는다.
 *
 * <h2>날씨 잡에 영향을 주지 않는다</h2>
 * 날씨 잡 러너({@code JobLauncherApplicationRunner}, order 0) 뒤에 돈다. 여기서 난 예외는 삼킨다 —
 * Trial 토큰은 24시간이면 만료되는데, 그때마다 날씨 수집 실행까지 실패로 끝나면 안 된다.
 * 핀은 DB 에 남아 있으므로 동기화가 실패해도 추천은 계속 나온다.
 *
 * <p>잡 이름을 {@code pinterestSyncJob} 으로 주면 이 설정은 꺼지고 기존 러너가 돈다. 두 번 돌지 않는다.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = "weatherCollectionJob")
class PinterestSyncAfterWeatherRunnerConfig {

    @Bean
    @ConditionalOnProperty(name = "spring.batch.job.enabled", havingValue = "true")
    PinterestSyncAfterWeatherRunner pinterestSyncAfterWeatherRunner(
            JobLauncher jobLauncher,
            @Qualifier("pinterestSyncJob") Job pinterestSyncJob,
            PinterestProperties properties) {
        return new PinterestSyncAfterWeatherRunner(jobLauncher, pinterestSyncJob, properties);
    }

    static final class PinterestSyncAfterWeatherRunner implements ApplicationRunner, Ordered {

        static final String EXECUTION_ID_PARAMETER = "execution.id";

        private final JobLauncher jobLauncher;
        private final Job pinterestSyncJob;
        private final PinterestProperties properties;

        PinterestSyncAfterWeatherRunner(JobLauncher jobLauncher, Job pinterestSyncJob, PinterestProperties properties) {
            this.jobLauncher = jobLauncher;
            this.pinterestSyncJob = pinterestSyncJob;
            this.properties = properties;
        }

        @Override
        public int getOrder() {
            return Ordered.LOWEST_PRECEDENCE;
        }

        @Override
        public void run(ApplicationArguments args) {
            if (!properties.hasAccessToken() || properties.boardIds().isEmpty()) {
                // 로컬·테스트처럼 Pinterest 를 안 쓰는 환경에서는 실패 로그를 남기지 않고 넘어간다.
                log.info("Pinterest sync after weather skipped. access token or board ids not configured");
                return;
            }
            try {
                // 러너를 거치지 않고 직접 띄우므로 RunIdIncrementer 가 적용되지 않는다. 실행마다 고유 값을 준다.
                var parameters = new JobParametersBuilder()
                        .addString(EXECUTION_ID_PARAMETER, UUID.randomUUID().toString())
                        .toJobParameters();
                JobExecution execution = jobLauncher.run(pinterestSyncJob, parameters);
                log.info("Pinterest sync after weather finished. status={}", execution.getStatus());
            } catch (Exception e) {
                log.warn("Pinterest sync after weather failed. Weather run is not affected", e);
            }
        }
    }
}
