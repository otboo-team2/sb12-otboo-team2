package com.otboo.clothes.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.clothes.extraction.CImageSelector.Channel;
import com.otboo.clothes.extraction.CImageSelector.CSelectorResult;
import com.otboo.clothes.extraction.CImageSelector.SelectionReason;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CImageSelectorTest {

    private final CImageSelector selector = new CImageSelector(8);

    @Test
    void fillsEightUniqueCandidatesByRoundRobinFeatureUnion() throws IOException {
        CSelectorResult result = selector.select(goldenScores());

        assertThat(result.selected()).extracting(CImageFeatureScore::candidateIndex)
                .containsExactly(3, 12, 1, 11, 10, 4, 9, 8);
        assertThat(result.selected()).extracting(CImageFeatureScore::candidateIndex)
                .doesNotHaveDuplicates();
        assertThat(result.selectedBy().get(3)).containsExactly(SelectionReason.TEXT);
        assertThat(result.channelRankings().get(Channel.TEXT)).containsExactly(
                3, 12, 1, 11, 10, 4, 9, 8, 2, 5, 6, 7);
    }

    @Test
    void returnsOnlyOneCandidateWhenOnlyOneWasDiscovered() {
        CImageFeatureScore only = score(17, 0.3, 0.2, 0.1, 100, 100);

        CSelectorResult result = selector.select(List.of(only));

        assertThat(result.selected()).containsExactly(only);
        assertThat(result.tableReserveCandidateIndex()).isNull();
        assertThat(result.textDetailReserveCandidateIndex()).isNull();
    }

    @Test
    void returnsEveryCandidateWhenFewerThanEightWereDiscovered() {
        List<CImageFeatureScore> candidates = List.of(
                score(14, 0.2, 0.1, 0.3, 100, 100),
                score(2, 0.4, 0.3, 0.1, 100, 100),
                score(9, 0.1, 0.2, 0.4, 100, 100));

        CSelectorResult result = selector.select(candidates);

        assertThat(result.selected()).extracting(CImageFeatureScore::candidateIndex)
                .containsExactly(2, 9, 14);
        assertThat(result.selected()).hasSize(candidates.size());
    }

    @Test
    void usesFirstDiscoveredCandidateToBreakFeatureTies() {
        List<CImageFeatureScore> candidates = List.of(
                score(9, 0.5, 0.5, 0.5, 100, 100),
                score(1, 0.5, 0.5, 0.5, 100, 100),
                score(5, 0.5, 0.5, 0.5, 100, 100));

        CSelectorResult result = selector.select(candidates);

        assertThat(result.selected()).extracting(CImageFeatureScore::candidateIndex)
                .containsExactly(9, 1, 5);
    }

    @Test
    void reservesAnUnselectedTopFourTableCandidateByReplacingDensityOnlySlot() {
        List<CImageFeatureScore> candidates = List.of(
                score(1, 1.00, 0.01, 0.01, 100, 100),
                score(2, 0.01, 1.00, 0.01, 100, 100),
                score(3, 0.01, 0.10, 1.00, 100, 100),
                score(4, 0.95, 0.01, 0.01, 100, 100),
                score(5, 0.01, 0.90, 0.01, 100, 100),
                score(6, 0.01, 0.20, 0.90, 100, 100),
                score(7, 0.90, 0.01, 0.01, 100, 100),
                score(8, 0.01, 0.80, 0.01, 100, 100),
                score(9, 0.01, 0.70, 0.01, 100, 100),
                score(10, 0.80, 0.01, 0.01, 100, 100),
                score(11, 0.01, 0.01, 0.80, 100, 100));

        CSelectorResult result = selector.select(candidates);

        assertThat(result.selected()).extracting(CImageFeatureScore::candidateIndex)
                .containsExactly(1, 2, 9, 4, 5, 6, 7, 8);
        assertThat(result.tableReserveCandidateIndex()).isEqualTo(9);
        assertThat(result.tableReserveReplacedCandidateIndex()).isEqualTo(3);
        assertThat(result.selectedBy().get(9)).containsExactly(SelectionReason.TABLE_RESERVE);
        assertThat(result.selected()).hasSize(8);
    }

    @Test
    void reservesLongTextCandidateByReplacingDensityOnlySlotWithoutAddingANinthImage() {
        List<CImageFeatureScore> candidates = List.of(
                score(1, 0.99, 0.01, 0.01, 100, 100),
                score(2, 0.01, 1.00, 0.01, 100, 100),
                score(3, 0.01, 0.01, 1.00, 100, 100),
                score(4, 0.98, 0.01, 0.01, 100, 100),
                score(5, 0.01, 0.90, 0.01, 100, 100),
                score(6, 0.02, 0.02, 0.90, 100, 100),
                score(7, 0.97, 0.01, 0.01, 100, 100),
                score(8, 0.01, 0.80, 0.01, 100, 100),
                score(9, 0.96, 0.01, 0.01, 100, 300),
                score(10, 0.01, 0.01, 0.80, 100, 100),
                score(11, 0.10, 0.02, 0.02, 100, 100));

        CSelectorResult result = selector.select(candidates);

        assertThat(result.selected()).extracting(CImageFeatureScore::candidateIndex)
                .containsExactly(1, 2, 9, 4, 5, 6, 7, 8);
        assertThat(result.textDetailReserveCandidateIndex()).isEqualTo(9);
        assertThat(result.textDetailReserveReplacedCandidateIndex()).isEqualTo(3);
        assertThat(result.selectedBy().get(9))
                .containsExactly(SelectionReason.TEXT_DETAIL_RESERVE);
        assertThat(result.selected()).hasSize(8);
    }

    @Test
    void neverReturnsTheSameCandidateTwiceWhenOneCandidateLeadsEveryChannel() {
        List<CImageFeatureScore> candidates = List.of(
                score(4, 0.99, 0.99, 0.99, 100, 100),
                score(7, 0.80, 0.80, 0.80, 100, 100));

        CSelectorResult result = selector.select(candidates);

        assertThat(result.selected()).extracting(CImageFeatureScore::candidateIndex)
                .containsExactly(4, 7);
    }

    @Test
    void rejectsDuplicateCandidateIndexesBecauseTheyMakeSelectionAmbiguous() {
        List<CImageFeatureScore> candidates = List.of(
                score(4, 0.9, 0.2, 0.1, 100, 100),
                score(4, 0.1, 0.2, 0.9, 100, 100));

        assertThatThrownBy(() -> selector.select(candidates))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static List<CImageFeatureScore> goldenScores() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream input = CImageSelectorTest.class.getResourceAsStream(
                "/clothes/extraction/c-selector-golden-scores.json")) {
            JsonNode root = mapper.readTree(input);
            List<CImageFeatureScore> scores = new ArrayList<>();
            for (JsonNode row : root.path("candidates")) {
                scores.add(score(
                        row.path("candidateIndex").asInt(),
                        row.path("textScore").asDouble(),
                        row.path("tableScore").asDouble(),
                        row.path("densityScore").asDouble(),
                        row.path("width").asInt(),
                        row.path("height").asInt()));
            }
            return List.copyOf(scores);
        }
    }

    private static CImageFeatureScore score(
            int index,
            double textScore,
            double tableScore,
            double densityScore,
            int width,
            int height
    ) {
        CImageCandidate candidate = new CImageCandidate(
                index,
                URI.create("https://example.test/candidate-" + index + ".jpg"),
                URI.create("https://example.test/candidate-" + index + ".jpg"),
                "image/jpeg",
                Path.of("candidate-" + index + ".jpg"),
                100L,
                width,
                height,
                "test-sha-" + index);
        return new CImageFeatureScore(
                candidate,
                textScore,
                textScore,
                densityScore,
                tableScore,
                tableScore,
                0.0,
                1);
    }
}
