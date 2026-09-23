package com.otboo.recommendation;

import com.otboo.clothes.ClothesService;
import com.otboo.clothes.dto.ClothesDto;
import com.otboo.clothes.entity.ClothesType;
import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.CommonErrorCode;
import com.otboo.feed.dto.OotdDto;
import com.otboo.feed.dto.ClothesAttributeWithDefDto;
import com.otboo.user.repository.ProfileRepository;
import com.otboo.user.preference.UserPreferenceRepository;
import com.otboo.weather.entity.Weather;
import com.otboo.weather.repository.WeatherRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final WeatherRepository weatherRepository;
    private final ProfileRepository profileRepository;
    private final UserPreferenceRepository preferenceRepository;
    private final ClothesService clothesService;
    private final WeatherClothesFilter weatherClothesFilter;

    @Transactional(readOnly = true)
    public RecommendationDto find(UUID userId, UUID weatherId) {
        return find(userId, weatherId, List.of());
    }

    @Transactional(readOnly = true)
    public RecommendationDto find(UUID userId, UUID weatherId, List<UUID> excludeClothesIds) {
        RecommendationCandidates candidates = findCandidates(userId, weatherId);
        if (excludeClothesIds == null || excludeClothesIds.isEmpty()) {
            return recommend(candidates);
        }
        Set<UUID> excludedIds = Set.copyOf(excludeClothesIds);
        return recommend(new RecommendationCandidates(
                candidates.weatherId(), candidates.userId(), candidates.temperature(),
                candidates.precipitationType(), candidates.temperatureSensitivity(),
                candidates.preferredStyles(), candidates.clothes().stream()
                        .filter(clothes -> !excludedIds.contains(clothes.id()))
                        .toList()));
    }

    @Transactional(readOnly = true)
    public RecommendationCandidates findCandidates(UUID userId, UUID weatherId) {
        Weather weather = weatherRepository.findById(weatherId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND)
                        .addDetail("weatherId", weatherId.toString()));

        Integer sensitivity = profileRepository.findByUserId(userId)
                .map(profile -> profile.getTemperatureSensitivity())
                .orElse(null);

        Set<String> preferredStyles = preferenceRepository.findAllByUserIdOrderByCreatedAtAscIdAsc(userId)
                .stream()
                .filter(preference -> "스타일".equals(preference.getSelectableValue().getDefinition().getName()))
                .map(preference -> preference.getSelectableValue().getValue())
                .collect(Collectors.toUnmodifiableSet());

        List<ClothesDto> suitable = clothesService.findAllForRecommendation(userId).stream()
                .filter(item -> weatherClothesFilter.isSuitable(
                        weather.getTemperatureCurrent().doubleValue(),
                        weather.getPrecipitationType(),
                        sensitivity,
                        item.type(),
                        warmthOf(item)))
                .toList();

        return new RecommendationCandidates(weatherId, userId,
                weather.getTemperatureCurrent().doubleValue(), weather.getPrecipitationType(),
                sensitivity, preferredStyles, suitable);
    }

    /** DB 재조회 없이 동일 후보로 기존 규칙 기반 추천을 구성한다. */
    public RecommendationDto recommend(RecommendationCandidates candidates) {
        List<ClothesDto> orderedCandidates = candidates.clothes().stream()
                .sorted((left, right) -> Boolean.compare(
                        matchesStyle(right, candidates.preferredStyles()),
                        matchesStyle(left, candidates.preferredStyles())))
                .toList();
        List<OotdDto> clothes = OotdCombinationPolicy.select(orderedCandidates).stream()
                .map(RecommendationService::toOotd)
                .toList();

        return new RecommendationDto(candidates.weatherId(), candidates.userId(), clothes);
    }

    private static boolean matchesStyle(ClothesDto clothes, Set<String> preferredStyles) {
        return clothes.attributes().stream().anyMatch(attribute ->
                "스타일".equals(attribute.definitionName())
                        && preferredStyles.contains(attribute.value()));
    }

    private static String warmthOf(ClothesDto clothes) {
        return clothes.attributes().stream()
                .filter(attribute -> "보온성".equals(attribute.definitionName()))
                .map(attribute -> attribute.value())
                .findFirst()
                .orElse(null);
    }

    private static OotdDto toOotd(ClothesDto clothes) {
        return new OotdDto(
                clothes.id(), clothes.name(), clothes.imageUrl(),
                clothes.type().name(), clothes.attributes().stream()
                        .map(attribute -> new ClothesAttributeWithDefDto(
                                attribute.definitionId(), attribute.definitionName(),
                                attribute.selectableValues(), attribute.value()))
                        .toList());
    }
}
