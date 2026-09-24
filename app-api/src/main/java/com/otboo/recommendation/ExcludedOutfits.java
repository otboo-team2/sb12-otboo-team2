package com.otboo.recommendation;

import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.CommonErrorCode;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 의상 순서와 무관하게 이미 노출된 OOTD 조합을 비교한다. */
public final class ExcludedOutfits {

    private ExcludedOutfits() {
    }

    public static Set<Set<UUID>> canonicalize(List<List<UUID>> outfits) {
        if (outfits == null || outfits.isEmpty()) {
            return Set.of();
        }
        Set<Set<UUID>> canonical = new HashSet<>();
        for (List<UUID> outfit : outfits) {
            if (outfit == null || outfit.isEmpty() || outfit.stream().anyMatch(java.util.Objects::isNull)) {
                throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
            }
            canonical.add(Set.copyOf(outfit));
        }
        return Set.copyOf(canonical);
    }

    public static boolean contains(Set<Set<UUID>> excluded, Collection<UUID> clothesIds) {
        return excluded.contains(Set.copyOf(clothesIds));
    }
}
