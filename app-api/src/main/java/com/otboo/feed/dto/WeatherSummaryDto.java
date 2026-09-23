package com.otboo.feed.dto;

import com.otboo.weather.SkyStatus;
import java.util.UUID;

/**
 * 피드에 박제된 그날의 날씨. 날씨 파트의 {@code WeatherDto} 와 달리 습도·풍속은 없다(스펙 그대로).
 *
 * <p>{@link SkyStatus} 는 날씨 파트({@code common-core} 의 {@code com.otboo.weather})가 소유한다.
 * 그 타입이 생기기 전에는 문자열, 실제 enum
 * <p>{@link PrecipitationDto}·{@link TemperatureDto} 는 날씨 파트의 {@code WeatherDto} 도 쓰는
 * 공유 타입이다. 📍 교현님 개발 파트 확인 후 삭제 예정
 */
public record WeatherSummaryDto(
        UUID weatherId,
        SkyStatus skyStatus,
        PrecipitationDto precipitation,
        TemperatureDto temperature
) {
}
