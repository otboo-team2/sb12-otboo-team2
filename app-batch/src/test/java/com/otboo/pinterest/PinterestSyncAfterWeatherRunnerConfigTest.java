package com.otboo.pinterest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.otboo.pinterest.PinterestSyncAfterWeatherRunnerConfig.PinterestSyncAfterWeatherRunner;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.Ordered;

class PinterestSyncAfterWeatherRunnerConfigTest {

    private final JobLauncher jobLauncher = mock(JobLauncher.class);
    private final Job job = mock(Job.class);

    @Test
    void launchesPinterestSyncWithUniqueParameterForEveryRun() throws Exception {
        var execution = mock(JobExecution.class);
        given(execution.getStatus()).willReturn(BatchStatus.COMPLETED);
        given(jobLauncher.run(eq(job), any())).willReturn(execution);
        var runner = runner("token", List.of("1", "2"));

        runner.run(new DefaultApplicationArguments());
        runner.run(new DefaultApplicationArguments());

        var parametersCaptor = ArgumentCaptor.forClass(JobParameters.class);
        verify(jobLauncher, times(2)).run(eq(job), parametersCaptor.capture());
        var first = parametersCaptor.getAllValues().get(0);
        var second = parametersCaptor.getAllValues().get(1);
        assertThat(first.getString("execution.id")).isNotBlank();
        assertThat(first.getString("execution.id")).isNotEqualTo(second.getString("execution.id"));
        assertThat(first.getParameters().get("execution.id").isIdentifying()).isTrue();
    }

    @Test
    void skipsWhenAccessTokenIsMissing() throws Exception {
        runner("", List.of("1")).run(new DefaultApplicationArguments());

        verify(jobLauncher, never()).run(any(), any());
    }

    @Test
    void skipsWhenBoardIdsAreEmpty() throws Exception {
        runner("token", List.of()).run(new DefaultApplicationArguments());

        verify(jobLauncher, never()).run(any(), any());
    }

    @Test
    void swallowsFailureSoWeatherRunIsNotAffected() throws Exception {
        given(jobLauncher.run(eq(job), any()))
                .willThrow(new JobExecutionAlreadyRunningException("already running"));
        var runner = runner("token", List.of("1"));

        assertThatCode(() -> runner.run(new DefaultApplicationArguments())).doesNotThrowAnyException();
    }

    @Test
    void runsAfterTheWeatherJobRunner() {
        // JobLauncherApplicationRunner 의 기본 order 는 0 이다.
        assertThat(runner("token", List.of("1")).getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE);
    }

    private PinterestSyncAfterWeatherRunner runner(String accessToken, List<String> boardIds) {
        return new PinterestSyncAfterWeatherRunner(
                jobLauncher, job, new PinterestProperties(null, accessToken, boardIds, null));
    }
}
