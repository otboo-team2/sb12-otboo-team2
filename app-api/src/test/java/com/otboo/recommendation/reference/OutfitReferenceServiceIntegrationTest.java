package com.otboo.recommendation.reference;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.common.test.IntegrationTestSupport;
import com.otboo.pinterest.entity.PinterestPin;
import com.otboo.pinterest.repository.PinterestPinRepository;
import com.otboo.pinterest.tag.OutfitTagParser;
import com.otboo.pinterest.tag.StyleTag;
import com.otboo.weather.PrecipitationType;
import com.otboo.weather.SkyStatus;
import com.otboo.weather.WindStrength;
import com.otboo.weather.entity.Weather;
import com.otboo.weather.repository.WeatherRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 날씨 → 태그 변환과 실제 MySQL 조회가 맞물리는지 확인한다.
 * 프로필이 없는 사용자라 성별로는 거르지 않는다.
 */
class OutfitReferenceServiceIntegrationTest extends IntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-09-21T00:00:00Z");

    @Autowired OutfitReferenceService service;
    @Autowired PinterestPinRepository pins;
    @Autowired WeatherRepository weathers;

    private final UUID userId = UUID.randomUUID();
    private Weather rainyEighteen;

    @BeforeEach
    void setUp() {
        pins.deleteAll();
        rainyEighteen = weathers.save(weather());
    }

    @AfterEach
    void tearDown() {
        pins.deleteAll();
        weathers.delete(rainyEighteen);
    }

    @Test
    @DisplayName("Pinterest 동기화 전 — 테이블이 비어 있으면 빈 목록")
    void emptyBeforeSync() {
        OutfitReferencesDto result = service.find(userId, rainyEighteen.getId(), null, null);

        assertThat(result.references()).isEmpty();
    }

    @Test
    @DisplayName("비 오는 18도 — 비 코디만, 앞뒤 기온 구간까지 나온다")
    void findsRainyPinsInAdjacentBands() {
        pins.save(pin("1", "@otboo temp:17-19 sky:rain style:casual gender:unisex"));
        pins.save(pin("2", "@otboo temp:20-22 sky:rain style:street gender:men"));      // 한 단계 따뜻한 구간
        pins.save(pin("3", "@otboo temp:17-19 sky:clear style:casual gender:unisex"));  // 맑은 날 → 제외
        pins.save(pin("4", "@otboo temp:5-8 sky:rain style:casual gender:unisex"));     // 먼 구간 → 제외
        pins.save(pin("5", "@otboo temp:17~19 sky:rain style:casual gender:unisex"));   // 태그 오타 → 제외

        OutfitReferencesDto result = service.find(userId, rainyEighteen.getId(), null, null);

        assertThat(result.references()).extracting(OutfitReferenceDto::pinId)
                .containsExactlyInAnyOrder("1", "2");
    }

    @Test
    @DisplayName("스타일을 고르면 그 스타일이 붙은 핀만")
    void filtersByStyle() {
        pins.save(pin("1", "@otboo temp:17-19 sky:rain style:casual gender:unisex"));
        pins.save(pin("2", "@otboo temp:17-19 sky:rain style:street,minimal gender:unisex"));

        OutfitReferencesDto result = service.find(userId, rainyEighteen.getId(), List.of(StyleTag.MINIMAL), null);

        assertThat(result.references()).singleElement()
                .satisfies(reference -> {
                    assertThat(reference.pinId()).isEqualTo("2");
                    assertThat(reference.styles()).containsExactly(StyleTag.MINIMAL, StyleTag.STREET);
                });
    }

    private static PinterestPin pin(String pinId, String description) {
        return PinterestPin.create(pinId, "1", "https://i.pinimg.com/" + pinId + ".jpg", null,
                "코디 " + pinId, description, OutfitTagParser.parse(description), NOW);
    }

    private static Weather weather() {
        return Weather.builder()
                .gridX(60).gridY(127).forecastedAt(NOW).forecastAt(NOW.plusSeconds(3600))
                .skyStatus(SkyStatus.CLOUDY).precipitationType(PrecipitationType.RAIN)
                .precipitationAmount(new BigDecimal("1.00"))
                .precipitationProbability(new BigDecimal("80.00"))
                .humidityCurrent(new BigDecimal("70.00"))
                .temperatureCurrent(new BigDecimal("18.40"))
                .temperatureMin(new BigDecimal("15.00"))
                .temperatureMax(new BigDecimal("20.00"))
                .windSpeed(new BigDecimal("3.00")).windSpeedAsWord(WindStrength.WEAK)
                .build();
    }
}
