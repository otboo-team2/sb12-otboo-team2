package com.otboo.weather;

import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.CommonErrorCode;
import com.otboo.common.http.ExternalApiClient;
import com.otboo.common.http.ExternalApiClientFactory;
import com.otboo.weather.exception.WeatherErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OpenWeatherMapClient {

    private final ExternalApiClient api;
    private final String apiKey;

    @Autowired
    public OpenWeatherMapClient(
            ExternalApiClientFactory factory,
            @Value("${otboo.weather.api-key:}") String apiKey
    ) {
        this.api = factory.create("weather", "https://api.openweathermap.org");
        this.apiKey = apiKey;
    }

    OpenWeatherMapClient(ExternalApiClient api, String apiKey) {
        this.api = api;
        this.apiKey = apiKey;
    }

    public OpenWeatherMapForecast fetch(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90
                || !Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new BusinessException(WeatherErrorCode.INVALID_COORDINATE);
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException(CommonErrorCode.EXTERNAL_API_ERROR);
        }

        OpenWeatherMapForecast forecast = api.get(
                "/data/2.5/forecast?lat=%s&lon=%s&appid=%s&units=metric"
                        .formatted(latitude, longitude, apiKey),
                OpenWeatherMapForecast.class);
        if (forecast == null || !"200".equals(forecast.cod())
                || forecast.list() == null || forecast.list().isEmpty()
                || forecast.list().stream().anyMatch(OpenWeatherMapClient::invalid)) {
            throw new BusinessException(CommonErrorCode.EXTERNAL_API_ERROR);
        }
        return forecast;
    }

    private static boolean invalid(OpenWeatherMapForecast.Entry entry) {
        return entry == null || entry.dt() == null || entry.main() == null
                || entry.main().temp() == null || entry.main().tempMin() == null
                || entry.main().tempMax() == null || entry.main().humidity() == null
                || entry.weather() == null || entry.weather().isEmpty()
                || entry.weather().stream().anyMatch(condition -> condition == null || condition.id() == null)
                || entry.clouds() == null || entry.clouds().all() == null
                || entry.wind() == null || entry.wind().speed() == null || entry.pop() == null;
    }
}
