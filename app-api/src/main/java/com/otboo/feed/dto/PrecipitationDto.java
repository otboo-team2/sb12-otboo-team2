package com.otboo.feed.dto;

import com.otboo.weather.PrecipitationType;

/**
 * 강수 정보.
 *
 * <p>스펙상 {@code WeatherSummaryDto}(피드)와 {@code WeatherDto}(날씨) 가 함께 쓰는 타입이다.
 * <b>날씨 파트는 새로 만들지 말고 이걸 재사용할 것.</b> 두 벌이 되면 필드가 어긋나는 순간
 * {@code /api/feeds} 와 {@code /api/weathers} 가 서로 다른 모양을 내려준다.
 *
 * @param amount      강수량 (mm)
 * @param probability 강수 확률 (%)
 */
public record PrecipitationDto(
        PrecipitationType type,
        Double amount,
        Double probability
) {
}
