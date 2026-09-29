package com.otboo.pinterest;

import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.boot.autoconfigure.batch.BatchProperties;
import org.springframework.boot.autoconfigure.batch.JobLauncherApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@code pinterestSyncJob} 을 이름만으로 실행할 수 있게 한다.
 *
 * <h2>왜 필요한가</h2>
 * {@code spring.batch.job.enabled} 를 앱 전체에서 {@code false} 로 뒀다({@code application.yml}
 * "기동만으로 전체 잡이 도는 것을 막는다"). 이 값이 {@code false} 면 Spring Boot 가 자동으로 만드는
 * 기본 {@link JobLauncherApplicationRunner} 는 아무 잡도 실행하지 않는다 — {@code --spring.batch.job.name}
 * 을 줘도 마찬가지다. 그래서 {@link PinterestSyncJobConfig} 의 사용법 주석대로
 * {@code java -jar app-batch.jar --spring.batch.job.name=pinterestSyncJob} 만 줘서는
 * 조용히 아무 일도 안 하고 끝난다({@link WeatherCollectionJobRunnerConfig 와 같은 이유} — 이름은
 * 다르지만 겪는 문제는 같다).
 *
 * <p>이 잡 전용 러너를 따로 두면, {@code spring.batch.job.name=pinterestSyncJob} 을 줬다는 것 자체가
 * "이 잡을 실행하겠다"는 뜻이므로 {@code job.enabled} 를 또 켤 필요가 없어진다.
 *
 * <h2>weather 쪽과 다르게 {@code execute} 를 오버라이드하지 않는 이유</h2>
 * {@code weatherCollectionJob} 은 incrementer 가 없어서 재실행마다 고유 파라미터를 직접 넣어줘야
 * 한다. {@link PinterestSyncJobConfig#pinterestSyncJob()} 은 이미 {@code RunIdIncrementer} 를
 * 쓰므로, Spring Boot 기본 {@link JobLauncherApplicationRunner} 그대로 써도 실행마다
 * {@code run.id} 가 자동으로 올라간다.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = "pinterestSyncJob")
class PinterestSyncJobRunnerConfig {

    @Bean
    JobLauncherApplicationRunner pinterestSyncJobLauncherApplicationRunner(
            JobLauncher jobLauncher,
            JobExplorer jobExplorer,
            JobRepository jobRepository,
            BatchProperties properties) {
        var runner = new JobLauncherApplicationRunner(jobLauncher, jobExplorer, jobRepository);
        runner.setJobName(properties.getJob().getName());
        return runner;
    }
}
