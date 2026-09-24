package com.otboo.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;

class WeatherCollectionJobRunnerConfigTest {

    @Test
    void addsUniqueIdentifyingParameterForEveryApplicationLaunch() throws Exception {
        var jobLauncher = mock(JobLauncher.class);
        var jobExplorer = mock(JobExplorer.class);
        var jobRepository = mock(JobRepository.class);
        var job = mock(Job.class);
        given(job.getName()).willReturn("weatherCollectionJob");
        given(job.getJobParametersIncrementer()).willReturn(new RunIdIncrementer());
        given(jobRepository.isJobInstanceExists(eq("weatherCollectionJob"), any()))
                .willAnswer(invocation -> {
                    JobParameters parameters = invocation.getArgument(1);
                    return parameters.isEmpty();
                });

        var runner = new WeatherCollectionJobRunnerConfig
                .UniqueWeatherExecutionJobLauncherApplicationRunner(
                        jobLauncher, jobExplorer, jobRepository);
        var suppliedParameters = new JobParametersBuilder()
                .addString("source", "ecs")
                .toJobParameters();

        runner.execute(job, suppliedParameters);
        runner.execute(job, suppliedParameters);

        var parametersCaptor = ArgumentCaptor.forClass(JobParameters.class);
        verify(jobLauncher, times(2)).run(any(), parametersCaptor.capture());
        var first = parametersCaptor.getAllValues().get(0);
        var second = parametersCaptor.getAllValues().get(1);

        assertThat(first.getString("source")).isEqualTo("ecs");
        assertThat(second.getString("source")).isEqualTo("ecs");
        assertThat(first.getString("execution.id")).isNotBlank();
        assertThat(second.getString("execution.id")).isNotBlank();
        assertThat(first.getString("execution.id"))
                .isNotEqualTo(second.getString("execution.id"));
        assertThat(first.getLong("run.id")).isNotNull();
        assertThat(second.getLong("run.id")).isNotNull();
        assertThat(first.getParameters().get("execution.id").isIdentifying()).isTrue();
        assertThat(second.getParameters().get("execution.id").isIdentifying()).isTrue();
    }

    @Test
    void leavesOtherBatchJobParametersUnchanged() throws Exception {
        var jobLauncher = mock(JobLauncher.class);
        var jobRepository = mock(JobRepository.class);
        var job = mock(Job.class);
        given(job.getName()).willReturn("pinterestSyncJob");
        given(jobRepository.isJobInstanceExists(any(), any())).willReturn(false);

        var runner = new WeatherCollectionJobRunnerConfig
                .UniqueWeatherExecutionJobLauncherApplicationRunner(
                        jobLauncher, mock(JobExplorer.class), jobRepository);
        var suppliedParameters = new JobParametersBuilder()
                .addString("source", "manual")
                .toJobParameters();

        runner.execute(job, suppliedParameters);

        var parametersCaptor = ArgumentCaptor.forClass(JobParameters.class);
        verify(jobLauncher).run(any(), parametersCaptor.capture());
        assertThat(parametersCaptor.getValue()).isEqualTo(suppliedParameters);
        assertThat(parametersCaptor.getValue().getString("execution.id")).isNull();
    }
}
