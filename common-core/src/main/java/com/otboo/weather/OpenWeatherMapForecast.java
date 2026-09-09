package com.otboo.weather;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenWeatherMapForecast(String cod, List<Entry> list) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entry(
            Long dt,
            Main main,
            List<Condition> weather,
            Clouds clouds,
            Wind wind,
            BigDecimal pop,
            Rain rain,
            Snow snow
    ) {
        public Instant forecastAt() {
            return Instant.ofEpochSecond(dt);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Main(
            BigDecimal temp,
            @JsonProperty("temp_min") BigDecimal tempMin,
            @JsonProperty("temp_max") BigDecimal tempMax,
            BigDecimal humidity
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Condition(Integer id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Clouds(Integer all) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Wind(BigDecimal speed) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Rain(@JsonProperty("3h") BigDecimal threeHours) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Snow(@JsonProperty("3h") BigDecimal threeHours) {
    }
}
