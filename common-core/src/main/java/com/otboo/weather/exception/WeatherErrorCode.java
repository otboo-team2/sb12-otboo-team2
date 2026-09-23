package com.otboo.weather.exception;

import com.otboo.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum WeatherErrorCode implements ErrorCode {

    INVALID_COORDINATE("WEATHER_001", HttpStatus.BAD_REQUEST,
            "위도는 -90~90, 경도는 -180~180 범위의 유한한 값이어야 합니다."),
    UNSUPPORTED_LOCATION("WEATHER_002", HttpStatus.BAD_REQUEST,
            "날씨 수집을 지원하지 않는 격자 범위입니다."),
    INVALID_WEATHER("WEATHER_003", HttpStatus.BAD_REQUEST,
            "날씨 필수값 또는 수치 범위가 올바르지 않습니다."),
    INVALID_LOCATION_NAMES("WEATHER_004", HttpStatus.BAD_REQUEST,
            "행정구역명은 비어 있지 않은 문자열 목록이어야 합니다.");

    private final String code;
    private final HttpStatus status;
    private final String message;
}
