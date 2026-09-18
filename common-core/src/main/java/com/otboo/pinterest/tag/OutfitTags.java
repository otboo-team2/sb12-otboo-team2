package com.otboo.pinterest.tag;

import java.util.Set;

/**
 * {@code @otboo} 줄에서 읽어낸 태그.
 *
 * <p>{@link TagStatus#MALFORMED} 이면 일부 값이 {@code null} 이거나 비어 있을 수 있다.
 * 읽어낸 만큼은 남겨둔다 — 오타 하나 때문에 나머지까지 버리면 어디가 틀렸는지 찾기 어렵다.
 */
public record OutfitTags(
        TempBand temp,
        SkyTag sky,
        Set<StyleTag> styles,
        Set<ItemTag> items,
        GenderTag gender
) {

    public static final OutfitTags EMPTY = new OutfitTags(null, null, Set.of(), Set.of(), null);

    public OutfitTags {
        styles = styles == null ? Set.of() : Set.copyOf(styles);
        items = items == null ? Set.of() : Set.copyOf(items);
    }
}
