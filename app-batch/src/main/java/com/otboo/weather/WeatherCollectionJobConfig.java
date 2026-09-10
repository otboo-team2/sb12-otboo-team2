package com.otboo.weather;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@RequiredArgsConstructor
public class WeatherCollectionJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final WeatherCollectionTasklet tasklet;
    private final WeatherCleanupTasklet cleanupTasklet;

    @Bean
    public Job weatherCollectionJob() {
        return new JobBuilder("weatherCollectionJob", jobRepository)
                .start(weatherCollectionStep())
                .build();
    }

    @Bean
    public Step weatherCollectionStep() {
        return new StepBuilder("weatherCollectionStep", jobRepository)
                .tasklet(tasklet, transactionManager)
                .build();
    }

    @Bean
    public Job weatherCleanupJob() {
        return new JobBuilder("weatherCleanupJob", jobRepository)
                .start(weatherCleanupStep())
                .build();
    }

    @Bean
    public Step weatherCleanupStep() {
        return new StepBuilder("weatherCleanupStep", jobRepository)
                .tasklet(cleanupTasklet, transactionManager)
                .build();
    }
}
