package com.otboo.clothes.extraction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class CImageSelector {

    private static final int TABLE_RESERVE_RANK = 4;
    private static final int TEXT_DETAIL_RANK_LIMIT = 20;
    private static final double MINIMUM_TEXT_DETAIL_SCORE = 0.95;
    private static final double MINIMUM_TEXT_DETAIL_ASPECT_RATIO = 2.0;
    private static final List<Channel> CHANNELS = List.of(
            Channel.TEXT,
            Channel.TABLE,
            Channel.DENSITY);

    private final int selectionLimit;

    public CImageSelector(int selectionLimit) {
        if (selectionLimit <= 0) {
            throw new IllegalArgumentException("selectionLimit must be positive");
        }
        this.selectionLimit = selectionLimit;
    }

    public CSelectorResult select(List<CImageFeatureScore> candidates) {
        Objects.requireNonNull(candidates, "candidates");
        Map<Integer, Integer> discoveryOrder = new LinkedHashMap<>();
        for (int position = 0; position < candidates.size(); position++) {
            CImageFeatureScore candidate = Objects.requireNonNull(
                    candidates.get(position), "candidate");
            if (discoveryOrder.putIfAbsent(candidate.candidateIndex(), position) != null) {
                throw new IllegalArgumentException("Candidate indexes must be unique");
            }
        }

        Map<Channel, List<CImageFeatureScore>> rankings = buildRankings(candidates, discoveryOrder);
        List<CImageFeatureScore> selected = selectRoundRobin(rankings);
        Map<Integer, List<SelectionReason>> selectedBy = findSelectionReasons(selected, rankings);
        Set<Integer> protectedIndexes = new LinkedHashSet<>();

        ReserveResult tableReserve = reserveTableCandidate(
                selected, selectedBy, protectedIndexes, rankings, discoveryOrder);
        ReserveResult textReserve = reserveTextDetailCandidate(
                selected, selectedBy, protectedIndexes, rankings, discoveryOrder);

        return new CSelectorResult(
                selected,
                toIndexRankings(rankings),
                selectedBy,
                tableReserve.candidateIndex(),
                tableReserve.replacedCandidateIndex(),
                textReserve.candidateIndex(),
                textReserve.replacedCandidateIndex());
    }

    private static Map<Channel, List<CImageFeatureScore>> buildRankings(
            List<CImageFeatureScore> candidates,
            Map<Integer, Integer> discoveryOrder
    ) {
        Map<Channel, List<CImageFeatureScore>> rankings = new EnumMap<>(Channel.class);
        for (Channel channel : CHANNELS) {
            Comparator<CImageFeatureScore> comparator = Comparator
                    .comparingDouble((CImageFeatureScore candidate) -> channelScore(candidate, channel))
                    .reversed()
                    .thenComparingInt(candidate -> discoveryOrder.get(candidate.candidateIndex()));
            rankings.put(channel, candidates.stream().sorted(comparator).toList());
        }
        return rankings;
    }

    private List<CImageFeatureScore> selectRoundRobin(
            Map<Channel, List<CImageFeatureScore>> rankings
    ) {
        List<CImageFeatureScore> selected = new ArrayList<>();
        Map<Channel, Integer> positions = new EnumMap<>(Channel.class);
        CHANNELS.forEach(channel -> positions.put(channel, 0));

        while (selected.size() < selectionLimit) {
            boolean progressed = false;
            for (Channel channel : CHANNELS) {
                List<CImageFeatureScore> ranking = rankings.get(channel);
                int position = positions.get(channel);
                while (position < ranking.size()) {
                    CImageFeatureScore candidate = ranking.get(position++);
                    positions.put(channel, position);
                    progressed = true;
                    if (!containsIndex(selected, candidate.candidateIndex())) {
                        selected.add(candidate);
                        break;
                    }
                }
                if (selected.size() >= selectionLimit) {
                    break;
                }
            }
            if (!progressed) {
                break;
            }
        }
        return selected;
    }

    private static Map<Integer, List<SelectionReason>> findSelectionReasons(
            List<CImageFeatureScore> selected,
            Map<Channel, List<CImageFeatureScore>> rankings
    ) {
        Map<Integer, List<SelectionReason>> reasons = new LinkedHashMap<>();
        for (CImageFeatureScore candidate : selected) {
            int bestRank = CHANNELS.stream()
                    .mapToInt(channel -> rankOf(rankings.get(channel), candidate.candidateIndex()))
                    .min()
                    .orElse(Integer.MAX_VALUE);
            List<SelectionReason> channels = CHANNELS.stream()
                    .filter(channel -> rankOf(rankings.get(channel), candidate.candidateIndex()) == bestRank)
                    .map(CImageSelector::reasonFor)
                    .toList();
            reasons.put(candidate.candidateIndex(), channels);
        }
        return reasons;
    }

    private static ReserveResult reserveTableCandidate(
            List<CImageFeatureScore> selected,
            Map<Integer, List<SelectionReason>> selectedBy,
            Set<Integer> protectedIndexes,
            Map<Channel, List<CImageFeatureScore>> rankings,
            Map<Integer, Integer> discoveryOrder
    ) {
        List<CImageFeatureScore> ranking = rankings.get(Channel.TABLE);
        for (int index = 0; index < Math.min(TABLE_RESERVE_RANK, ranking.size()); index++) {
            CImageFeatureScore candidate = ranking.get(index);
            if (!containsIndex(selected, candidate.candidateIndex())) {
                Integer replaced = replacementSlot(
                        selected, selectedBy, protectedIndexes, discoveryOrder, Channel.TABLE);
                if (replaced == null) {
                    return ReserveResult.none();
                }
                replace(selected, selectedBy, candidate, replaced, SelectionReason.TABLE_RESERVE);
                protectedIndexes.add(candidate.candidateIndex());
                return new ReserveResult(candidate.candidateIndex(), replaced);
            }
        }
        return ReserveResult.none();
    }

    private static ReserveResult reserveTextDetailCandidate(
            List<CImageFeatureScore> selected,
            Map<Integer, List<SelectionReason>> selectedBy,
            Set<Integer> protectedIndexes,
            Map<Channel, List<CImageFeatureScore>> rankings,
            Map<Integer, Integer> discoveryOrder
    ) {
        List<CImageFeatureScore> ranking = rankings.get(Channel.TEXT);
        List<RankedCandidate> eligible = new ArrayList<>();
        for (int index = 0; index < Math.min(TEXT_DETAIL_RANK_LIMIT, ranking.size()); index++) {
            CImageFeatureScore candidate = ranking.get(index);
            if (!containsIndex(selected, candidate.candidateIndex())
                    && candidate.textScore() >= MINIMUM_TEXT_DETAIL_SCORE
                    && candidate.aspectRatio() >= MINIMUM_TEXT_DETAIL_ASPECT_RATIO) {
                eligible.add(new RankedCandidate(index + 1, candidate));
            }
        }
        RankedCandidate candidate = eligible.stream()
                .max(Comparator.comparingDouble(
                                (RankedCandidate item) -> item.candidate().detailTextReserveScore())
                        .thenComparingInt(item -> -item.rank()))
                .orElse(null);
        if (candidate == null || selected.isEmpty()) {
            return ReserveResult.none();
        }

        Integer replaced = replacementSlot(
                selected, selectedBy, protectedIndexes, discoveryOrder, Channel.TEXT);
        if (replaced == null) {
            return ReserveResult.none();
        }
        CImageFeatureScore reserveCandidate = candidate.candidate();
        replace(selected, selectedBy, reserveCandidate, replaced, SelectionReason.TEXT_DETAIL_RESERVE);
        return new ReserveResult(reserveCandidate.candidateIndex(), replaced);
    }

    private static Integer replacementSlot(
            List<CImageFeatureScore> selected,
            Map<Integer, List<SelectionReason>> selectedBy,
            Set<Integer> protectedIndexes,
            Map<Integer, Integer> discoveryOrder,
            Channel replacementChannel
    ) {
        List<CImageFeatureScore> densityOnly = selected.stream()
                .filter(candidate -> !protectedIndexes.contains(candidate.candidateIndex()))
                .filter(candidate -> selectedBy.get(candidate.candidateIndex())
                        .equals(List.of(SelectionReason.DENSITY)))
                .toList();
        if (!densityOnly.isEmpty()) {
            Comparator<CImageFeatureScore> comparator = Comparator
                    .comparingDouble((CImageFeatureScore candidate) -> replacementChannel == Channel.TABLE
                            ? candidate.tableScore()
                            : candidate.detailTextReserveScore())
                    .thenComparingInt(candidate -> discoveryOrder.get(candidate.candidateIndex()));
            return densityOnly.stream().min(comparator)
                    .map(CImageFeatureScore::candidateIndex)
                    .orElse(null);
        }

        for (int index = selected.size() - 1; index >= 0; index--) {
            int candidateIndex = selected.get(index).candidateIndex();
            if (!protectedIndexes.contains(candidateIndex)) {
                return candidateIndex;
            }
        }
        return null;
    }

    private static void replace(
            List<CImageFeatureScore> selected,
            Map<Integer, List<SelectionReason>> selectedBy,
            CImageFeatureScore reserve,
            int replacedIndex,
            SelectionReason reserveReason
    ) {
        int position = indexOf(selected, replacedIndex);
        selected.set(position, reserve);
        selectedBy.remove(replacedIndex);
        selectedBy.put(reserve.candidateIndex(), List.of(reserveReason));
    }

    private static Map<Channel, List<Integer>> toIndexRankings(
            Map<Channel, List<CImageFeatureScore>> rankings
    ) {
        Map<Channel, List<Integer>> result = new EnumMap<>(Channel.class);
        CHANNELS.forEach(channel -> result.put(channel, rankings.get(channel).stream()
                .map(CImageFeatureScore::candidateIndex)
                .toList()));
        return result;
    }

    private static double channelScore(CImageFeatureScore candidate, Channel channel) {
        return switch (channel) {
            case TEXT -> candidate.textScore();
            case TABLE -> candidate.tableScore();
            case DENSITY -> candidate.densityScore();
        };
    }

    private static SelectionReason reasonFor(Channel channel) {
        return switch (channel) {
            case TEXT -> SelectionReason.TEXT;
            case TABLE -> SelectionReason.TABLE;
            case DENSITY -> SelectionReason.DENSITY;
        };
    }

    private static int rankOf(List<CImageFeatureScore> ranking, int candidateIndex) {
        for (int rank = 0; rank < ranking.size(); rank++) {
            if (ranking.get(rank).candidateIndex() == candidateIndex) {
                return rank + 1;
            }
        }
        return Integer.MAX_VALUE;
    }

    private static boolean containsIndex(List<CImageFeatureScore> candidates, int candidateIndex) {
        return indexOf(candidates, candidateIndex) >= 0;
    }

    private static int indexOf(List<CImageFeatureScore> candidates, int candidateIndex) {
        for (int index = 0; index < candidates.size(); index++) {
            if (candidates.get(index).candidateIndex() == candidateIndex) {
                return index;
            }
        }
        return -1;
    }

    public enum Channel {
        TEXT,
        TABLE,
        DENSITY
    }

    public enum SelectionReason {
        TEXT,
        TABLE,
        DENSITY,
        TABLE_RESERVE,
        TEXT_DETAIL_RESERVE
    }

    public record CSelectorResult(
            List<CImageFeatureScore> selected,
            Map<Channel, List<Integer>> channelRankings,
            Map<Integer, List<SelectionReason>> selectedBy,
            Integer tableReserveCandidateIndex,
            Integer tableReserveReplacedCandidateIndex,
            Integer textDetailReserveCandidateIndex,
            Integer textDetailReserveReplacedCandidateIndex
    ) {
        public CSelectorResult {
            selected = List.copyOf(selected);
            Map<Channel, List<Integer>> rankingCopy = new EnumMap<>(Channel.class);
            channelRankings.forEach((channel, ranking) -> rankingCopy.put(channel, List.copyOf(ranking)));
            channelRankings = Collections.unmodifiableMap(rankingCopy);
            Map<Integer, List<SelectionReason>> reasonCopy = new LinkedHashMap<>();
            selectedBy.forEach((candidateIndex, reasons) ->
                    reasonCopy.put(candidateIndex, List.copyOf(reasons)));
            selectedBy = Collections.unmodifiableMap(reasonCopy);
        }
    }

    private record RankedCandidate(int rank, CImageFeatureScore candidate) {}

    private record ReserveResult(Integer candidateIndex, Integer replacedCandidateIndex) {
        private static ReserveResult none() {
            return new ReserveResult(null, null);
        }
    }
}
