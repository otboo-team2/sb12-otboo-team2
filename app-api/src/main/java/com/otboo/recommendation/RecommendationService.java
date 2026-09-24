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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
        return find(userId, weatherId, excludeClothesIds, List.of());
    }

    @Transactional(readOnly = true)
    public RecommendationDto find(
            UUID userId, UUID weatherId, List<UUID> excludeClothesIds,
            List<List<UUID>> excludedOutfits) {
        RecommendationCandidates candidates = findCandidates(userId, weatherId);
        if (excludeClothesIds != null && !excludeClothesIds.isEmpty()) {
            Set<UUID> excludedIds = Set.copyOf(excludeClothesIds);
            candidates = new RecommendationCandidates(
                    candidates.weatherId(), candidates.userId(), candidates.temperature(),
                    candidates.precipitationType(), candidates.temperatureSensitivity(),
                    candidates.preferredStyles(), candidates.clothes().stream()
                            .filter(clothes -> !excludedIds.contains(clothes.id()))
                            .toList());
        }
        return excludedOutfits == null || excludedOutfits.isEmpty()
                ? recommend(candidates)
                : recommend(candidates, excludedOutfits);
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
        return recommend(candidates, List.of());
    }

    public RecommendationDto recommend(
            RecommendationCandidates candidates, List<List<UUID>> excludedOutfits) {
        List<ClothesDto> orderedCandidates = candidates.clothes().stream()
                .sorted((left, right) -> Boolean.compare(
                        matchesStyle(right, candidates.preferredStyles()),
                        matchesStyle(left, candidates.preferredStyles())))
                .toList();
        List<List<UUID>> history = excludedOutfits == null ? List.of() : excludedOutfits;
        Set<Set<UUID>> excluded = ExcludedOutfits.canonicalize(history);
        List<OotdDto> clothes = selectBestUnseen(orderedCandidates, history, excluded).stream()
                .map(RecommendationService::toOotd)
                .toList();

        return new RecommendationDto(candidates.weatherId(), candidates.userId(), clothes);
    }

    private static List<ClothesDto> selectBestUnseen(
            List<ClothesDto> orderedCandidates, List<List<UUID>> history,
            Set<Set<UUID>> excluded) {
        List<List<ClothesType>> compositions = new ArrayList<>();
        addComposition(compositions, OotdCombinationPolicy.select(orderedCandidates));
        addComposition(compositions, OotdCombinationPolicy.select(orderedCandidates.stream()
                .filter(item -> item.type() != ClothesType.DRESS)
                .toList()));
        List<ClothesDto> dressComposition = OotdCombinationPolicy.select(orderedCandidates.stream()
                .filter(item -> item.type() != ClothesType.TOP && item.type() != ClothesType.BOTTOM)
                .toList());
        if (dressComposition.stream().anyMatch(item -> item.type() == ClothesType.DRESS)) {
            addComposition(compositions, dressComposition);
        }

        List<List<ClothesDto>> unseen = new ArrayList<>();
        for (List<ClothesType> composition : compositions) {
            List<List<ClothesDto>> choices = composition.stream()
                    .map(type -> orderedCandidates.stream().filter(item -> item.type() == type).toList())
                    .toList();
            collectUnseen(orderedCandidates, choices, 0, new LinkedHashSet<>(), excluded, unseen);
        }
        Map<UUID, Integer> usageCounts = usageCounts(history);
        Set<UUID> lastOutfit = history.isEmpty()
                ? Set.of() : Set.copyOf(history.get(history.size() - 1));
        List<ClothesDto> best = List.of();
        int bestUsage = Integer.MAX_VALUE;
        int bestOverlap = Integer.MAX_VALUE;
        for (List<ClothesDto> outfit : unseen) {
            int usage = outfit.stream().mapToInt(item -> usageCounts.getOrDefault(item.id(), 0)).sum();
            int overlap = (int) outfit.stream().filter(item -> lastOutfit.contains(item.id())).count();
            if (usage < bestUsage || (usage == bestUsage && overlap < bestOverlap)) {
                best = outfit;
                bestUsage = usage;
                bestOverlap = overlap;
            }
        }
        return best;
    }

    private static void addComposition(
            List<List<ClothesType>> compositions, List<ClothesDto> selection) {
        List<ClothesType> types = selection.stream().map(ClothesDto::type).toList();
        if (!types.isEmpty() && compositions.stream()
                .noneMatch(existing -> Set.copyOf(existing).equals(Set.copyOf(types)))) {
            compositions.add(types);
        }
    }

    private static void collectUnseen(
            List<ClothesDto> orderedCandidates, List<List<ClothesDto>> choices, int index,
            Set<UUID> selectedIds, Set<Set<UUID>> excluded, List<List<ClothesDto>> unseen) {
        if (index == choices.size()) {
            List<ClothesDto> selected = orderedCandidates.stream()
                    .filter(item -> selectedIds.contains(item.id()))
                    .toList();
            if (OotdCombinationPolicy.isValid(selected)
                    && !ExcludedOutfits.contains(excluded, selectedIds)) {
                unseen.add(selected);
            }
            return;
        }
        for (ClothesDto choice : choices.get(index)) {
            selectedIds.add(choice.id());
            collectUnseen(orderedCandidates, choices, index + 1, selectedIds, excluded, unseen);
            selectedIds.remove(choice.id());
        }
    }

    private static Map<UUID, Integer> usageCounts(List<List<UUID>> history) {
        Map<UUID, Integer> counts = new HashMap<>();
        for (List<UUID> outfit : history) {
            Set.copyOf(outfit).forEach(id -> counts.merge(id, 1, Integer::sum));
        }
        return counts;
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
