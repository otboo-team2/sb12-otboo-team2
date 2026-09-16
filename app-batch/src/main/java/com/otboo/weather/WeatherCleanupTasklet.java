package com.otboo.weather;

import com.otboo.weather.repository.WeatherRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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
    private final MeterRegistry meterRegistry;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        Timer.Sample timer = Timer.start(meterRegistry);
        Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);
        long deleted = weatherRepository.deleteByForecastAtBefore(cutoff);
        meterRegistry.counter("weather.cleanup.deleted").increment(deleted);
        timer.stop(meterRegistry.timer("weather.cleanup.duration"));
        log.info("Finished weather cleanup. deleted_count={}, cutoff={}", deleted, cutoff);
        return RepeatStatus.FINISHED;
    }
}
