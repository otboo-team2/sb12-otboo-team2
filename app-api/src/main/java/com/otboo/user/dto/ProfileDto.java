package com.otboo.user.dto;

import com.otboo.user.entity.Gender;
import com.otboo.user.entity.Profile;
import com.otboo.user.entity.User;
import com.otboo.weather.dto.WeatherApiLocation;
import com.otboo.weather.entity.WeatherRegion;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 프로필 응답. 필드명이 프론트 {@code ProfileDto} 와 일치해야 한다.
 *
 * <p>{@code name} 은 프로필이 아니라 계정에서 온다.
 */
public record ProfileDto(
        UUID userId,
        String name,
        Gender gender,
        LocalDate birthDate,
        WeatherApiLocation location,
        Integer temperatureSensitivity,
        String profileImageUrl
) {

    public static ProfileDto from(Profile profile) {
        return new ProfileDto(
                profile.getUser().getId(),
                profile.getUser().getName(),
                profile.getGender(),
                profile.getBirthDate(),
                toLocation(profile),
                profile.getTemperatureSensitivity(),
                profile.getProfileImageUrl());
    }

    /** 프로필 행이 아직 없는 계정. 가입 이전에 만들어진 계정에서 나올 수 있다. */
    public static ProfileDto empty(User user) {
        return new ProfileDto(user.getId(), user.getName(), null, null, null, null, null);
    }

    private static WeatherApiLocation toLocation(Profile profile) {
        WeatherRegion region = profile.getRegion();
        if (profile.getLatitude() == null && region == null) {
            return null;        // 위치를 한 번도 설정하지 않았다
        }
        return new WeatherApiLocation(
                profile.getLatitude() == null ? null : profile.getLatitude().doubleValue(),
                profile.getLongitude() == null ? null : profile.getLongitude().doubleValue(),
                region == null ? null : region.getGridX(),
                region == null ? null : region.getGridY(),
                region == null ? null : region.getLocationNames());
    }
}
