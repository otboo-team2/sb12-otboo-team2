package com.otboo.recommendation.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.CommonErrorCode;
import com.otboo.pinterest.entity.PinterestPin;
import com.otboo.pinterest.repository.PinterestPinRepository;
import com.otboo.pinterest.tag.GenderTag;
import com.otboo.pinterest.tag.OutfitTagParser;
import com.otboo.pinterest.tag.SkyTag;
import com.otboo.pinterest.tag.StyleTag;
import com.otboo.pinterest.tag.TempBand;
import com.otboo.user.entity.Gender;
import com.otboo.user.entity.Profile;
import com.otboo.user.repository.ProfileRepository;
import com.otboo.weather.PrecipitationType;
import com.otboo.weather.SkyStatus;
import com.otboo.weather.entity.Weather;
import com.otboo.weather.repository.WeatherRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutfitReferenceServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID WEATHER_ID = UUID.randomUUID();

    @Mock WeatherRepository weatherRepository;
    @Mock ProfileRepository profileRepository;
    @Mock PinterestPinRepository pinRepository;
    @Mock Weather weather;
    @Mock Profile profile;
    @InjectMocks OutfitReferenceService service;

    @Test
    @DisplayName("기온은 앞뒤 구간까지 넓히고 날씨는 넓히지 않는다")
    void widensTemperatureButNotSky() {
        givenCloudyWeatherAt("18.4");
        givenPins(List.of());

        OutfitReferencesDto result = service.find(USER_ID, WEATHER_ID, null, null);

        assertThat(result.tempBand()).isEqualTo(TempBand.T17_19);
        assertThat(result.sky()).isEqualTo(SkyTag.CLOUDY);
        verify(pinRepository).findTaggedFor(
                List.of(TempBand.T17_19, TempBand.T20_22, TempBand.T12_16),
                Set.of(SkyTag.CLOUDY),
                EnumSet.allOf(GenderTag.class),
                List.of(),
                OutfitReferenceService.DEFAULT_LIMIT);
    }

    @Test
    @DisplayName("동기화 전이라 핀이 없으면 빈 목록을 돌려준다 — 오류가 아니다")
    void returnsEmptyBeforeSync() {
        givenCloudyWeatherAt("18.4");
        givenPins(List.of());

        OutfitReferencesDto result = service.find(USER_ID, WEATHER_ID, null, null);

        assertThat(result.weatherId()).isEqualTo(WEATHER_ID);
        assertThat(result.references()).isEmpty();
    }

    @Test
    @DisplayName("사진마다 원본 핀 주소와 스타일을 담는다")
    void mapsPinWithSourceLinkAndStyles() {
        givenCloudyWeatherAt("18.4");
        String description = "@otboo temp:17-19 sky:cloudy style:street,minimal item:knit gender:unisex";
        givenPins(List.of(PinterestPin.create("813744226420795884", "1",
                "https://i.pinimg.com/600x/a.jpg", "https://shop.example.com/item", "니트 레이어드",
                description, OutfitTagParser.parse(description), Instant.now())));

        OutfitReferenceDto reference = service.find(USER_ID, WEATHER_ID, null, null).references().getFirst();

        assertThat(reference.pinId()).isEqualTo("813744226420795884");
        assertThat(reference.imageUrl()).isEqualTo("https://i.pinimg.com/600x/a.jpg");
        assertThat(reference.pinUrl()).isEqualTo("https://www.pinterest.com/pin/813744226420795884/");
        assertThat(reference.link()).isEqualTo("https://shop.example.com/item");
        assertThat(reference.title()).isEqualTo("니트 레이어드");
        // item 태그는 섞이지 않고, 스타일은 선언 순서로 정렬된다
        assertThat(reference.styles()).containsExactly(StyleTag.MINIMAL, StyleTag.STREET);
    }

    @Test
    @DisplayName("요청한 스타일과 한도를 그대로 넘긴다")
    void passesStylesAndLimit() {
        givenCloudyWeatherAt("18.4");
        givenPins(List.of());

        service.find(USER_ID, WEATHER_ID, List.of(StyleTag.MINIMAL), 5);

        verify(pinRepository).findTaggedFor(any(), any(), any(), eq(List.of(StyleTag.MINIMAL)), eq(5));
    }

    @Test
    @DisplayName("프로필 성별에 맞는 핀과 공용 핀을 함께 찾는다")
    void usesProfileGenderWithUnisex() {
        givenCloudyWeatherAt("18.4");
        given(profileRepository.findByUserId(USER_ID)).willReturn(Optional.of(profile));
        given(profile.getGender()).willReturn(Gender.FEMALE);
        givenPins(List.of());

        service.find(USER_ID, WEATHER_ID, null, null);

        verify(pinRepository).findTaggedFor(
                any(), any(), eq(EnumSet.of(GenderTag.WOMEN, GenderTag.UNISEX)), any(), anyInt());
    }

    @Test
    @DisplayName("날씨가 없으면 핀을 찾지 않고 404")
    void rejectsUnknownWeather() {
        given(weatherRepository.findById(WEATHER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.find(USER_ID, WEATHER_ID, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND);
        verifyNoInteractions(pinRepository);
    }

    @ParameterizedTest(name = "{0} + {1} → {2}")
    @CsvSource({
            "CLEAR,         NONE,      CLEAR",
            "MOSTLY_CLOUDY, NONE,      CLOUDY",
            "CLOUDY,        NONE,      CLOUDY",
            "CLOUDY,        RAIN,      RAIN",
            "CLEAR,         SHOWER,    RAIN",
            "CLOUDY,        SNOW,      SNOW",
            "CLOUDY,        RAIN_SNOW, SNOW",
    })
    @DisplayName("강수가 하늘 상태보다 먼저다")
    void mapsSky(SkyStatus skyStatus, PrecipitationType precipitation, SkyTag expected) {
        assertThat(OutfitReferenceService.skyOf(skyStatus, precipitation)).isEqualTo(expected);
    }

    @Test
    @DisplayName("성별을 모르거나 기타면 거르지 않는다")
    void mapsGender() {
        assertThat(OutfitReferenceService.gendersFor(Gender.MALE))
                .containsExactlyInAnyOrder(GenderTag.MEN, GenderTag.UNISEX);
        assertThat(OutfitReferenceService.gendersFor(Gender.FEMALE))
                .containsExactlyInAnyOrder(GenderTag.WOMEN, GenderTag.UNISEX);
        assertThat(OutfitReferenceService.gendersFor(Gender.OTHER)).containsExactlyInAnyOrder(GenderTag.values());
        assertThat(OutfitReferenceService.gendersFor(null)).containsExactlyInAnyOrder(GenderTag.values());
    }

    @Test
    @DisplayName("한도는 기본 12, 1~30 으로 자른다")
    void clampsLimit() {
        assertThat(OutfitReferenceService.clampLimit(null)).isEqualTo(12);
        assertThat(OutfitReferenceService.clampLimit(0)).isEqualTo(1);
        assertThat(OutfitReferenceService.clampLimit(100)).isEqualTo(30);
    }

    private void givenCloudyWeatherAt(String celsius) {
        given(weatherRepository.findById(WEATHER_ID)).willReturn(Optional.of(weather));
        given(weather.getTemperatureCurrent()).willReturn(new BigDecimal(celsius));
        given(weather.getSkyStatus()).willReturn(SkyStatus.CLOUDY);
        given(weather.getPrecipitationType()).willReturn(PrecipitationType.NONE);
    }

    private void givenPins(List<PinterestPin> pins) {
        given(pinRepository.findTaggedFor(anyCollection(), anyCollection(), anyCollection(), anyCollection(), anyInt()))
                .willReturn(pins);
    }
}
