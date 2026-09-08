package com.otboo.user.dto;

import com.otboo.user.entity.Gender;
import com.otboo.weather.dto.WeatherApiLocation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

/**
 * 프로필 부분 수정. <b>모든 항목이 선택</b>이고, 보내지 않은 항목은 바뀌지 않는다.
 *
 * <p>프론트는 {@code multipart/form-data} 의 {@code request} 파트에 이 JSON 을 담아 보낸다
 * ({@code users.ts:updateProfile}). 이미지는 {@code image} 파트로 따로 온다.
 *
 * @param name                   계정 이름. 프로필이 아니라 {@code users.name} 을 바꾼다
 * @param temperatureSensitivity 1(추위에 민감) ~ 5(더위에 민감). DB CHECK 와 같은 범위다
 */
public record ProfileUpdateRequest(
        // 회원가입과 같은 규칙이어야 한다. 가입은 막고 수정은 통과하는 이름이 생기면 안 된다.
        @Pattern(regexp = "^[가-힣a-zA-Z0-9]{2,20}$",
                message = "이름은 2~20자의 한글, 영문, 숫자만 가능합니다.")
        String name,

        Gender gender,

        @Past(message = "생년월일은 오늘보다 이전이어야 합니다.")
        LocalDate birthDate,

        WeatherApiLocation location,

        @Min(value = 1, message = "온도 민감도는 1~5 사이여야 합니다.")
        @Max(value = 5, message = "온도 민감도는 1~5 사이여야 합니다.")
        Integer temperatureSensitivity
) {

    public ProfileUpdateRequest {
        // 공백만 보낸 이름은 "안 바꿈"으로 본다. 트림을 검증보다 먼저 해야 " 홍길동 " 이 통과한다.
        if (name != null) {
            name = name.trim();
            if (name.isEmpty()) {
                name = null;
            }
        }
    }

    /**
     * 위치는 격자 좌표와 지명이 모두 있어야 지역을 찾을 수 있다.
     *
     * <p>프론트는 {@code GET /api/weathers/location} 응답을 그대로 실어 보내므로 셋 다 채워져 온다.
     * 일부만 온 요청은 위치를 건드리지 않는다 — {@code WeatherRegion} 이 지명 없는 지역을
     * 거부하기 때문에, 여기서 거르지 않으면 프로필 수정이 날씨 에러코드로 실패한다.
     */
    public boolean hasResolvableLocation() {
        return location != null
                && location.x() != null
                && location.y() != null
                && !location.locationNames().isEmpty();
    }
}
