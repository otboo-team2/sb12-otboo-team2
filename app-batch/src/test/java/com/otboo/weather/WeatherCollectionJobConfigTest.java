package com.otboo.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.transaction.PlatformTransactionManager;

class WeatherCollectionJobConfigTest {

    @Test
    void incrementsRunIdForEachWeatherCollectionExecution() {
        var config = new WeatherCollectionJobConfig(
                mock(JobRepository.class),
                mock(PlatformTransactionManager.class),
                mock(WeatherCollectionTasklet.class),
                mock(WeatherCleanupTasklet.class));

        var job = config.weatherCollectionJob();
        var incrementer = job.getJobParametersIncrementer();

        assertThat(incrementer).isInstanceOf(RunIdIncrementer.class);
        var firstRun = incrementer.getNext(new JobParameters());
        var secondRun = incrementer.getNext(firstRun);
        assertThat(firstRun.getLong("run.id")).isEqualTo(1L);
        assertThat(secondRun.getLong("run.id")).isEqualTo(2L);
    }
}
