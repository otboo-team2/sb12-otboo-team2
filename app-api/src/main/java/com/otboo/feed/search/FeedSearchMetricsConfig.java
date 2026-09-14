package com.otboo.feed.search;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * {@code FeedService} 에 주입되는 검색. MySQL -> {@link TimedFeedSearch} 감싼 것.
 *
 * <p>{@link MysqlFeedSearch} 도 {@link FeedSearchPort} 빈이라 {@code @Primary} 로 이쪽을 고르게 한다.
 * 검색 엔진을 추가할 때는 이 빈에서 엔진마다 {@link TimedFeedSearch} 로 감싼 뒤 조합한다.
 */
@Configuration
public class FeedSearchMetricsConfig {

    @Bean
    @Primary
    public FeedSearchPort feedSearchPort(MysqlFeedSearch mysqlFeedSearch, MeterRegistry registry) {
        return new TimedFeedSearch("mysql", mysqlFeedSearch, registry);
    }
}
