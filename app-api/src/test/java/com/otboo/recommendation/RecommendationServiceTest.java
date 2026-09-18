package com.otboo.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.otboo.clothes.ClothesService;
import com.otboo.clothes.dto.ClothesDto;
import com.otboo.clothes.entity.ClothesType;
import com.otboo.clothes.entity.ClothesAttributeSelectableValue;
import com.otboo.clothes.entity.ClothesAttributeDefinition;
import com.otboo.clothes.dto.ClothesAttributeWithDefDto;
import com.otboo.user.entity.Profile;
import com.otboo.user.preference.UserPreference;
import com.otboo.user.preference.UserPreferenceRepository;
import com.otboo.user.repository.ProfileRepository;
import com.otboo.weather.PrecipitationType;
import com.otboo.weather.entity.Weather;
import com.otboo.weather.repository.WeatherRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock WeatherRepository weatherRepository;
    @Mock ProfileRepository profileRepository;
    @Mock UserPreferenceRepository preferenceRepository;
    @Mock ClothesService clothesService;
    @Mock WeatherClothesFilter weatherClothesFilter;
    @Mock Weather weather;
    @Mock Profile profile;
    @Mock UserPreference preference;
    @Mock ClothesAttributeSelectableValue selectableValue;
    @Mock ClothesAttributeDefinition definition;

    @Test
    void appliesWeatherSensitivityOwnershipAndPreferenceOrdering() {
        UUID userId = UUID.randomUUID();
        UUID weatherId = UUID.randomUUID();
        ClothesDto preferred = clothes(UUID.randomUUID(), "선호 상의", ClothesType.TOP, "캐주얼");
        ClothesDto other = clothes(UUID.randomUUID(), "다른 의상", ClothesType.BOTTOM, "포멀");
        ClothesDto anotherBottom = clothes(UUID.randomUUID(), "두 번째 하의", ClothesType.BOTTOM, "포멀");
        ClothesDto filtered = clothes(UUID.randomUUID(), "더운 날 외투", ClothesType.OUTER, "캐주얼");

        given(weatherRepository.findById(weatherId)).willReturn(Optional.of(weather));
        given(weather.getTemperatureCurrent()).willReturn(BigDecimal.valueOf(27));
        given(weather.getPrecipitationType()).willReturn(PrecipitationType.NONE);
        given(profileRepository.findByUserId(userId)).willReturn(Optional.of(profile));
        given(profile.getTemperatureSensitivity()).willReturn(5);
        given(preferenceRepository.findAllByUserIdOrderByCreatedAtAscIdAsc(userId))
                .willReturn(List.of(preference));
        given(preference.getSelectableValue()).willReturn(selectableValue);
        given(selectableValue.getDefinition()).willReturn(definition);
        given(definition.getName()).willReturn("스타일");
        given(selectableValue.getValue()).willReturn("캐주얼");
        given(clothesService.findAllForRecommendation(userId))
                .willReturn(List.of(other, anotherBottom, preferred, filtered));
        given(weatherClothesFilter.isSuitable(27.0, PrecipitationType.NONE, 5, ClothesType.TOP, null))
                .willReturn(true);
        given(weatherClothesFilter.isSuitable(27.0, PrecipitationType.NONE, 5, ClothesType.BOTTOM, null))
                .willReturn(true);
        given(weatherClothesFilter.isSuitable(27.0, PrecipitationType.NONE, 5, ClothesType.OUTER, null))
                .willReturn(false);


        RecommendationService service = new RecommendationService(
                weatherRepository, profileRepository, preferenceRepository,
                clothesService, weatherClothesFilter);
        RecommendationDto result = service.find(userId, weatherId);

        assertThat(result.clothes()).extracting("clothesId")
                .containsExactly(preferred.id(), other.id());
        assertThat(result.clothes()).extracting(item -> item.type())
                .doesNotHaveDuplicates();
        assertThat(result.clothes()).noneMatch(item -> item.clothesId().equals(filtered.id()));
        verify(clothesService).findAllForRecommendation(userId);
        verify(weatherClothesFilter).isSuitable(27.0, PrecipitationType.NONE, 5, ClothesType.OUTER, null);
    }

    private ClothesDto clothes(UUID id, String name, ClothesType type, String style) {
        return new ClothesDto(id, UUID.randomUUID(), name, null, type, false,
                List.of(new ClothesAttributeWithDefDto(
                        UUID.randomUUID(), "스타일", List.of(style), style)));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3})
    void retainsAllSuitableCandidatesBeforeCategorySelection(int sensitivity) {
        UUID userId = UUID.randomUUID();
        UUID weatherId = UUID.randomUUID();
        ClothesDto first = clothes(UUID.randomUUID(), "상의 A", ClothesType.TOP, "캐주얼");
        ClothesDto second = clothes(UUID.randomUUID(), "상의 B", ClothesType.TOP, "포멀");
        ClothesDto thick = new ClothesDto(UUID.randomUUID(), userId, "의상 C", null,
                ClothesType.TOP, false, List.of(new ClothesAttributeWithDefDto(
                        UUID.randomUUID(), "보온성", List.of("두꺼움"), "두꺼움")));
        ClothesDto tooWarm = new ClothesDto(UUID.randomUUID(), userId, "의상 D", null,
                ClothesType.OUTER, false, List.of(new ClothesAttributeWithDefDto(
                        UUID.randomUUID(), "보온성", List.of("매우 두꺼움"), "매우 두꺼움")));

        given(weatherRepository.findById(weatherId)).willReturn(Optional.of(weather));
        given(weather.getTemperatureCurrent()).willReturn(BigDecimal.valueOf(23));
        given(weather.getPrecipitationType()).willReturn(PrecipitationType.NONE);
        given(profileRepository.findByUserId(userId)).willReturn(Optional.of(profile));
        given(profile.getTemperatureSensitivity()).willReturn(sensitivity);
        given(preferenceRepository.findAllByUserIdOrderByCreatedAtAscIdAsc(userId))
                .willReturn(List.of());
        given(clothesService.findAllForRecommendation(userId))
                .willReturn(List.of(first, second, thick, tooWarm));

        RecommendationService service = new RecommendationService(
                weatherRepository, profileRepository, preferenceRepository,
                clothesService, new WeatherClothesFilter());
        RecommendationCandidates candidates = service.findCandidates(userId, weatherId);

        assertThat(candidates.clothes())
                .containsExactlyElementsOf(sensitivity == 1
                        ? List.of(first, second, thick) : List.of(first, second));
        assertThat(candidates.temperature()).isEqualTo(23.0);
        assertThat(candidates.temperatureSensitivity()).isEqualTo(sensitivity);
        assertThat(candidates.userId()).isEqualTo(userId);
        assertThat(candidates.weatherId()).isEqualTo(weatherId);
        assertThat(service.recommend(candidates).clothes()).extracting("clothesId")
                .containsExactly(first.id());
        verify(clothesService).findAllForRecommendation(userId);
    }
}
