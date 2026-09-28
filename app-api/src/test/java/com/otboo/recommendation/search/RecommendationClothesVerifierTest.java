package com.otboo.recommendation.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.otboo.clothes.entity.Clothes;
import com.otboo.clothes.repository.ClothesRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class RecommendationClothesVerifierTest {

    private final ClothesRepository repository = mock(ClothesRepository.class);
    private final RecommendationClothesVerifier verifier = new RecommendationClothesVerifier(repository);
    private final UUID ownerId = UUID.randomUUID();

    @Test
    void keepsOwnedExistingCandidatesInEsRelevanceOrder() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        Clothes firstClothes = clothes(first, ownerId);
        Clothes secondClothes = clothes(second, ownerId);
        given(repository.findAllById(List.of(second, first)))
                .willReturn(List.of(firstClothes, secondClothes));

        assertThat(verifier.verify(ownerId, List.of(first, second), List.of(second, first)))
                .containsExactly(second, first);
    }

    @Test
    void removesStaleForeignAndNonWeatherCandidateIdsWithoutReorderingSurvivors() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID stale = UUID.randomUUID();
        UUID foreign = UUID.randomUUID();
        UUID notWeatherCandidate = UUID.randomUUID();
        Clothes firstClothes = clothes(first, ownerId);
        Clothes foreignClothes = clothes(foreign, UUID.randomUUID());
        Clothes secondClothes = clothes(second, ownerId);
        given(repository.findAllById(List.of(second, stale, foreign, first)))
                .willReturn(List.of(firstClothes, foreignClothes, secondClothes));

        assertThat(verifier.verify(ownerId, List.of(first, second, stale, foreign),
                List.of(second, stale, foreign, notWeatherCandidate, first)))
                .containsExactly(second, first);
        verify(repository).findAllById(List.of(second, stale, foreign, first));
    }

    @Test
    void skipsDatabaseWhenEsResultIsEmpty() {
        assertThat(verifier.verify(ownerId, List.of(UUID.randomUUID()), List.of())).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void skipsDatabaseWhenNoEsIdWasWeatherEligible() {
        assertThat(verifier.verify(ownerId, List.of(UUID.randomUUID()), List.of(UUID.randomUUID())))
                .isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void propagatesMysqlFailure() {
        UUID id = UUID.randomUUID();
        var failure = new DataAccessResourceFailureException("MySQL unavailable");
        given(repository.findAllById(List.of(id))).willThrow(failure);

        assertThatThrownBy(() -> verifier.verify(ownerId, List.of(id), List.of(id)))
                .isSameAs(failure);
    }

    private Clothes clothes(UUID id, UUID owner) {
        Clothes clothes = mock(Clothes.class);
        given(clothes.getId()).willReturn(id);
        given(clothes.getOwnerId()).willReturn(owner);
        return clothes;
    }
}
