package com.otboo.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;


import org.junit.jupiter.api.Test;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.transaction.PlatformTransactionManager;
import jakarta.persistence.EntityManagerFactory;

class WeatherCollectionJobConfigTest {

    @Test
    void createsWeatherCollectionJobWithoutNumericRunIdIncrementer() {
        var config = new WeatherCollectionJobConfig(
                mock(JobRepository.class),
                mock(PlatformTransactionManager.class),
                new WeatherRegionItemReader(mock(EntityManagerFactory.class)),
                mock(WeatherCollectionItemProcessor.class),
                mock(WeatherCollectionItemWriter.class),
                mock(WeatherCleanupTasklet.class));

        var job = config.weatherCollectionJob();

        assertThat(job.getJobParametersIncrementer()).isNull();
    }

}
