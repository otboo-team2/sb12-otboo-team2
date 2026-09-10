package com.otboo.weather;

import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.CommonErrorCode;
import com.otboo.weather.entity.WeatherRegion;
import com.otboo.weather.repository.WeatherRegionRepository;
import java.util.List;
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
public class WeatherCollectionTasklet implements Tasklet {

    private final WeatherRegionRepository regionRepository;
    private final OpenWeatherMapWeatherService weatherService;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        List<WeatherRegion> regions = regionRepository.findAll();
        int successCount = 0;
        int failureCount = 0;

        log.info("Starting weather collection. region_count={}", regions.size());
        for (WeatherRegion region : regions) {
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
