package com.otboo.weather.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.otboo.common.test.IntegrationTestSupport;
import com.otboo.weather.PrecipitationType;
import com.otboo.weather.SkyStatus;
import com.otboo.weather.WindStrength;
import com.otboo.weather.entity.Weather;
import com.otboo.weather.entity.WeatherRegion;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
class WeatherRepositoryTest extends IntegrationTestSupport {

    private static final Instant ANNOUNCED = Instant.parse("2026-09-07T00:00:00.123456Z");
    private static final Instant TARGET = Instant.parse("2026-09-07T03:00:00Z");

    @Autowired WeatherRegionRepository regions;
    @Autowired WeatherRepository weathers;
    @Autowired EntityManager entityManager;

    @Test
    void roundTripsRegionJsonAndAuditFields() {
        var region = regions.saveAndFlush(
                WeatherRegion.create(60, 127, List.of("서울특별시", "강남구")));
        entityManager.clear();

        var found = regions.findByGridXAndGridY(60, 127).orElseThrow();
        assertThat(found.getId()).isEqualTo(region.getId());
        assertThat(found.getLocationNames()).containsExactly("서울특별시", "강남구");
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
        assertThat(regions.findByGridXAndGridY(98, 76)).isEmpty();
    }

    @Test
    void roundTripsWeatherAndSelectsOneAnnouncementInForecastOrder() {
        weathers.saveAndFlush(weather(ANNOUNCED, TARGET.plusSeconds(3600)));
        var saved = weathers.saveAndFlush(weather(ANNOUNCED, TARGET));
        weathers.saveAndFlush(weather(ANNOUNCED.plusSeconds(3600), TARGET));
        entityManager.clear();

        var found = weathers.findByGridXAndGridYAndForecastedAtAndForecastAt(
                60, 127, ANNOUNCED, TARGET).orElseThrow();
        assertThat(found).usingRecursiveComparison().isEqualTo(saved);
        assertThat(found.getTemperatureCurrent()).isEqualByComparingTo("-2.50");
        assertThat(found.getHumidityComparedToDayBefore()).isNull();
        assertThat(found.getTemperatureComparedToDayBefore()).isNull();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
        assertThat(weathers.findByGridXAndGridYAndForecastedAtOrderByForecastAtAsc(
                60, 127, ANNOUNCED)).extracting(Weather::getForecastAt)
                .containsExactly(TARGET, TARGET.plusSeconds(3600));
        assertThat(weathers.findByGridXAndGridYAndForecastedAtOrderByForecastAtAsc(
                98, 76, ANNOUNCED)).isEmpty();
    }

    @Test
    void rejectsDuplicateRegion() {
        regions.saveAndFlush(WeatherRegion.create(60, 127, List.of("서울특별시")));
        assertThatThrownBy(() -> regions.saveAndFlush(
                WeatherRegion.create(60, 127, List.of("서울특별시"))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateForecastInSameAnnouncement() {
        weathers.saveAndFlush(weather(ANNOUNCED, TARGET));
        assertThatThrownBy(() -> weathers.saveAndFlush(weather(ANNOUNCED, TARGET)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Weather weather(Instant announced, Instant target) {
        return Weather.builder()
                .gridX(60).gridY(127).forecastedAt(announced).forecastAt(target)
                .skyStatus(SkyStatus.CLOUDY).precipitationType(PrecipitationType.SNOW)
                .precipitationAmount(new BigDecimal("1.20"))
                .precipitationProbability(new BigDecimal("80.00"))
                .humidityCurrent(new BigDecimal("70.00"))
                .temperatureCurrent(new BigDecimal("-2.50"))
                .temperatureMin(new BigDecimal("-5.00"))
                .temperatureMax(new BigDecimal("1.00"))
                .windSpeed(new BigDecimal("4.00")).windSpeedAsWord(WindStrength.MODERATE)
                .build();
    }
}
