package com.otboo.weather;

import com.otboo.weather.entity.Weather;
import com.otboo.weather.entity.WeatherRegion;
import java.util.List;

public record WeatherCollectionResult(WeatherRegion region, List<Weather> weather) {
}
