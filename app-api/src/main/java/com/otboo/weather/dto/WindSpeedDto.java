package com.otboo.weather.dto;

import com.otboo.weather.WindStrength;

public record WindSpeedDto(Double speed, WindStrength asWord) {
}
