package com.otboo.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.clothes.dto.ClothesDto;
import com.otboo.clothes.entity.ClothesType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OotdCombinationPolicyTest {

    @Test
    void choosesOneMainCombinationWhenDressAndSeparatesAreBothAvailable() {
        ClothesDto top = clothes(ClothesType.TOP);
        ClothesDto bottom = clothes(ClothesType.BOTTOM);
        ClothesDto dress = clothes(ClothesType.DRESS);

        assertThat(OotdCombinationPolicy.select(List.of(top, bottom, dress)))
                .containsExactly(top, bottom);
    }

    @Test
    void buildsSeparatesWithOptionalOuterAndShoes() {
        ClothesDto top = clothes(ClothesType.TOP);
        ClothesDto bottom = clothes(ClothesType.BOTTOM);
        ClothesDto outer = clothes(ClothesType.OUTER);
        ClothesDto shoes = clothes(ClothesType.SHOES);

        assertThat(OotdCombinationPolicy.select(List.of(top, bottom, outer, shoes)))
                .containsExactly(top, bottom, outer, shoes);
    }

    @Test
    void buildsDressCombinationWithOptionalOuterAndShoes() {
        ClothesDto dress = clothes(ClothesType.DRESS);
        ClothesDto outer = clothes(ClothesType.OUTER);
        ClothesDto shoes = clothes(ClothesType.SHOES);

        assertThat(OotdCombinationPolicy.select(List.of(dress, outer, shoes)))
                .containsExactly(dress, outer, shoes);
    }

    @Test
    void keepsDressAsTheOnlyMainItemWhenSeparatesAreIncomplete() {
        ClothesDto top = clothes(ClothesType.TOP);
        ClothesDto dress = clothes(ClothesType.DRESS);

        assertThat(OotdCombinationPolicy.select(List.of(top, dress))).containsExactly(dress);
    }

    @Test
    void safelyReturnsAvailableSupportedItemsWithoutInventingMissingMainItem() {
        ClothesDto top = clothes(ClothesType.TOP);
        ClothesDto shoes = clothes(ClothesType.SHOES);
        ClothesDto underwear = clothes(ClothesType.UNDERWEAR);

        assertThat(OotdCombinationPolicy.select(List.of(top, shoes, underwear)))
                .containsExactly(top, shoes);
    }

    @Test
    void rejectsConflictingOrDuplicateAiCombinations() {
        assertThat(OotdCombinationPolicy.isValid(List.of(
                clothes(ClothesType.DRESS), clothes(ClothesType.TOP), clothes(ClothesType.BOTTOM))))
                .isFalse();
        assertThat(OotdCombinationPolicy.isValid(List.of(
                clothes(ClothesType.TOP), clothes(ClothesType.TOP))))
                .isFalse();
    }

    private static ClothesDto clothes(ClothesType type) {
        return new ClothesDto(UUID.randomUUID(), UUID.randomUUID(), type.name(), null, type, false, List.of());
    }
}
