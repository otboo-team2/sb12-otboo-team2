package com.otboo.weather.dto;

import java.util.List;

/**
 * 기상청 단기예보 위치 정보. Swagger 의 {@code WeatherAPILocation} 과 필드명이 같아야 한다.
 *
 * <p>프론트는 {@code GET /api/weathers/location} 으로 이 값을 받아 프로필 수정에 그대로 실어 보낸다
 * ({@code LocationInput.tsx}). 그래서 날씨 파트와 사용자 파트가 같은 타입을 쓴다.
 *
 * <p><b>⚠️ 날씨 파트(박교현)와 공유하는 타입이다.</b> KAN-22 에서 프로필 위치를 다루느라 먼저 만들었다.
 * 날씨 파트에 같은 것이 생기면 하나로 합친다.
 *
 * @param latitude      위도
 * @param longitude     경도
 * @param x             기상청 격자 X
 * @param y             기상청 격자 Y
 * @param locationNames 지명. 예: {@code ["서울특별시", "강남구"]}
 */
public record WeatherApiLocation(
        Double latitude,
        Double longitude,
        Integer x,
        Integer y,
        List<String> locationNames
) {

    public WeatherApiLocation {
        locationNames = locationNames == null ? List.of() : List.copyOf(locationNames);
    }
}
