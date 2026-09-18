package com.otboo.pinterest;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Pinterest 핀 동기화 잡.
 *
 * <pre>
 * java -jar app-batch.jar --spring.batch.job.name=pinterestSyncJob
 * </pre>
 *
 * <h2>{@code RunIdIncrementer} 가 있어야 두 번 돈다</h2>
 * 같은 잡을 같은 파라미터로 다시 실행하면 Spring Batch 는 이미 끝난 실행으로 보고 거절한다.
 * 이 잡은 하루에 한 번씩 계속 돌아야 하므로 실행마다 {@code run.id} 가 올라가게 둔다.
 * Spring Boot 의 잡 러너가 incrementer 가 있는 잡에는 다음 파라미터를 알아서 넣어준다.
 */
@Configuration
@RequiredArgsConstructor
public class PinterestSyncJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final PinterestSyncTasklet tasklet;

    @Bean
    public Job pinterestSyncJob() {
        return new JobBuilder("pinterestSyncJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(pinterestSyncStep())
                .build();
    }

    /**
     * 스텝 하나에 트랜잭션 하나다. 보드를 여러 개 돌아도 커밋은 끝에 한 번이라,
     * 중간에 든 보드의 결과도 태스크릿이 예외를 삼키고 끝까지 가야 남는다.
     */
    @Bean
    public Step pinterestSyncStep() {
        return new StepBuilder("pinterestSyncStep", jobRepository)
                .tasklet(tasklet, transactionManager)
                .build();
    }
}
