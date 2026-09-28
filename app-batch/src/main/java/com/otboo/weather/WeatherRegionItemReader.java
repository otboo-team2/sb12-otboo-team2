package com.otboo.weather;

import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WeatherRegionItemReader {

    private final EntityManagerFactory entityManagerFactory;

    @Value("${otboo.weather.batch.chunk-size:10}")
    private int configuredPageSize;

    public JpaPagingItemReader<com.otboo.weather.entity.WeatherRegion> create() {
        int pageSize = Math.max(configuredPageSize, 1);
        return new JpaPagingItemReaderBuilder<com.otboo.weather.entity.WeatherRegion>()
                .name("weatherRegionItemReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString("select region from WeatherRegion region order by region.id")
                .pageSize(pageSize)
                .build();
    }
}
