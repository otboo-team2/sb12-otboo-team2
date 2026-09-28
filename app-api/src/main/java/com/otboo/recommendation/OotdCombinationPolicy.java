package com.otboo.recommendation;

import com.otboo.clothes.dto.ClothesDto;
import com.otboo.clothes.entity.ClothesType;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** 한 번에 착용 가능한 기본 OOTD 조합 규칙. */
public final class OotdCombinationPolicy {

    private static final Set<ClothesType> OPTIONAL_TYPES = Set.of(
            ClothesType.OUTER, ClothesType.SHOES, ClothesType.ACCESSORY,
            ClothesType.HAT, ClothesType.BAG, ClothesType.SCARF);

    private OotdCombinationPolicy() {
    }

    public static List<ClothesDto> select(List<ClothesDto> orderedCandidates) {
        boolean hasTop = hasType(orderedCandidates, ClothesType.TOP);
        boolean hasBottom = hasType(orderedCandidates, ClothesType.BOTTOM);
        boolean hasDress = hasType(orderedCandidates, ClothesType.DRESS);
        boolean useDress = hasDress && (!(hasTop && hasBottom)
                || firstIndexOf(orderedCandidates, ClothesType.DRESS)
                < firstIndexOfEither(orderedCandidates, ClothesType.TOP, ClothesType.BOTTOM));

        Set<ClothesType> allowedMain = useDress
                ? Set.of(ClothesType.DRESS)
                : hasTop && hasBottom
                        ? Set.of(ClothesType.TOP, ClothesType.BOTTOM)
                        : hasDress
                                ? Set.of(ClothesType.DRESS)
                                : hasTop
                                        ? Set.of(ClothesType.TOP)
                                        : hasBottom ? Set.of(ClothesType.BOTTOM) : Set.of();
        Set<ClothesType> selectedTypes = EnumSet.noneOf(ClothesType.class);
        return orderedCandidates.stream()
                .filter(item -> allowedMain.contains(item.type()) || OPTIONAL_TYPES.contains(item.type()))
                .filter(item -> selectedTypes.add(item.type()))
                .toList();
    }

    public static boolean isValid(List<ClothesDto> clothes) {
        Set<ClothesType> types = EnumSet.noneOf(ClothesType.class);
        for (ClothesDto item : clothes) {
            if (item == null || (!OPTIONAL_TYPES.contains(item.type())
                    && item.type() != ClothesType.TOP
                    && item.type() != ClothesType.BOTTOM
                    && item.type() != ClothesType.DRESS)
                    || !types.add(item.type())) {
                return false;
            }
        }
        return !types.contains(ClothesType.DRESS)
                || (!types.contains(ClothesType.TOP) && !types.contains(ClothesType.BOTTOM));
    }

    private static boolean hasType(List<ClothesDto> clothes, ClothesType type) {
        return clothes.stream().anyMatch(item -> item.type() == type);
    }

    private static int firstIndexOf(List<ClothesDto> clothes, ClothesType type) {
        for (int index = 0; index < clothes.size(); index++) {
            if (clothes.get(index).type() == type) {
                return index;
            }
        }
        return Integer.MAX_VALUE;
    }

    private static int firstIndexOfEither(
            List<ClothesDto> clothes, ClothesType first, ClothesType second) {
        return Math.min(firstIndexOf(clothes, first), firstIndexOf(clothes, second));
    }
}
