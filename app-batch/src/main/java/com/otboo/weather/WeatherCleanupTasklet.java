package com.otboo.weather;

import com.otboo.weather.repository.WeatherRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherCleanupTasklet implements Tasklet {

    private final WeatherRepository weatherRepository;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);
        long deleted = weatherRepository.deleteByForecastAtBefore(cutoff);
        log.info("Finished weather cleanup. deleted_count={}, cutoff={}", deleted, cutoff);
        return RepeatStatus.FINISHED;
    }
}
