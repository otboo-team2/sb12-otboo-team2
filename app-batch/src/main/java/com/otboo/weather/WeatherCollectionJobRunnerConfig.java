package com.otboo.weather;

import java.util.UUID;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.boot.autoconfigure.batch.BatchProperties;
import org.springframework.boot.autoconfigure.batch.JobLauncherApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = "weatherCollectionJob")
class WeatherCollectionJobRunnerConfig {

    @Bean
    @ConditionalOnProperty(name = "spring.batch.job.enabled", havingValue = "true")
    JobLauncherApplicationRunner weatherCollectionJobLauncherApplicationRunner(
            JobLauncher jobLauncher,
            JobExplorer jobExplorer,
            JobRepository jobRepository,
            BatchProperties properties) {
        var runner = new UniqueWeatherExecutionJobLauncherApplicationRunner(
                jobLauncher, jobExplorer, jobRepository);
        runner.setJobName(properties.getJob().getName());
        return runner;
    }

    static final class UniqueWeatherExecutionJobLauncherApplicationRunner
            extends JobLauncherApplicationRunner {

        static final String WEATHER_COLLECTION_JOB = "weatherCollectionJob";
        static final String EXECUTION_ID_PARAMETER = "execution.id";

        UniqueWeatherExecutionJobLauncherApplicationRunner(
                JobLauncher jobLauncher,
                JobExplorer jobExplorer,
                JobRepository jobRepository) {
            super(jobLauncher, jobExplorer, jobRepository);
        }

        @Override
        protected void execute(Job job, JobParameters jobParameters)
                throws JobExecutionAlreadyRunningException, JobRestartException,
                JobInstanceAlreadyCompleteException, JobParametersInvalidException {
            if (!WEATHER_COLLECTION_JOB.equals(job.getName())) {
                super.execute(job, jobParameters);
                return;
            }
            var uniqueParameters = new JobParametersBuilder(jobParameters)
                    .addString(EXECUTION_ID_PARAMETER, UUID.randomUUID().toString())
                    .toJobParameters();
            super.execute(job, uniqueParameters);
        }
    }
}
