package com.otboo.feed.dto;

/**
 * 온도 정보.
 *
 * <p>{@link PrecipitationDto} 와 마찬가지로 날씨 파트의 {@code WeatherDto} 도 쓰는 타입이다.
 * 새로 만들지 말 것.
 *
 * @param comparedToDayBefore 전날 대비 변동. 수집 첫날은 비교 대상이 없어 {@code null} 이다.
 */
public record TemperatureDto(
        Double current,
        Double comparedToDayBefore,
        Double min,
        Double max
) {
}
