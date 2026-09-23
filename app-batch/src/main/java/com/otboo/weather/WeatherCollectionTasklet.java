package com.otboo.weather;

import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.CommonErrorCode;
import com.otboo.weather.entity.WeatherRegion;
import com.otboo.weather.repository.WeatherRegionRepository;
import java.util.List;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherCollectionTasklet implements Tasklet {

    private final WeatherRegionRepository regionRepository;
    private final OpenWeatherMapWeatherService weatherService;
    private final MeterRegistry meterRegistry;

    @Value("${otboo.weather.batch.chunk-size:10}")
    private int chunkSize = 10;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        List<WeatherRegion> regions = regionRepository.findAll();
        int successCount = 0;
        int failureCount = 0;
        Timer.Sample timer = Timer.start(meterRegistry);
        int effectiveChunkSize = Math.max(chunkSize, 1);

        log.info("Starting weather collection. region_count={}", regions.size());
        for (int start = 0; start < regions.size(); start += effectiveChunkSize) {
            int end = Math.min(start + effectiveChunkSize, regions.size());
            log.debug("Processing weather collection chunk. start={}, end={}", start, end);
            for (WeatherRegion region : regions.subList(start, end)) {
                try {
                    var coordinate = WeatherGridConverter.toCoordinate(region.getGridX(), region.getGridY());
                    weatherService.fetchAndSave(coordinate.latitude(), coordinate.longitude(),
                            region.getGridX(), region.getGridY());
                    successCount++;
                } catch (BusinessException e) {
                    if (!isExternalFailure(e)) {
                        throw e;
                    }
                    failureCount++;
                    log.warn("Weather collection failed. grid_x={}, grid_y={}, error_code={}",
                            region.getGridX(), region.getGridY(), e.getErrorCode().getCode());
                }
            }
        }
        meterRegistry.counter("weather.collection.success").increment(successCount);
        meterRegistry.counter("weather.collection.failure").increment(failureCount);
        timer.stop(meterRegistry.timer("weather.collection.duration"));
        log.info("Finished weather collection. success_count={}, failure_count={}",
                successCount, failureCount);
        return RepeatStatus.FINISHED;
    }

    private boolean isExternalFailure(BusinessException exception) {
        var code = exception.getErrorCode();
        return code == CommonErrorCode.EXTERNAL_API_ERROR
                || code == CommonErrorCode.EXTERNAL_API_TIMEOUT
                || code == CommonErrorCode.EXTERNAL_API_LIMIT_EXCEEDED;
    }
}
