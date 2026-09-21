package com.otboo.recommendation.search;

import com.otboo.clothes.entity.Clothes;
import com.otboo.clothes.repository.ClothesRepository;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** ES가 반환한 ID를 현재 MySQL 의상, 소유권, 날씨 후보 목록과 대조한다. */
@Service
@RequiredArgsConstructor
public class RecommendationClothesVerifier {

    private final ClothesRepository clothesRepository;

    @Transactional(readOnly = true)
    public List<UUID> verify(UUID ownerId, Collection<UUID> candidateIds, List<UUID> retrievedIds) {
        if (retrievedIds.isEmpty()) {
            return List.of();
        }
        Set<UUID> allowed = Set.copyOf(candidateIds);
        List<UUID> eligibleIds = retrievedIds.stream().filter(allowed::contains).distinct().toList();
        if (eligibleIds.isEmpty()) {
            return List.of();
        }
        Set<UUID> existingOwnedIds = clothesRepository.findAllById(eligibleIds).stream()
                .filter(clothes -> ownerId.equals(clothes.getOwnerId()))
                .map(Clothes::getId)
                .collect(Collectors.toSet());
        return eligibleIds.stream().filter(existingOwnedIds::contains).toList();
    }
}
