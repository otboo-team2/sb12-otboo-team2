package com.otboo.virtualtryon.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.clothes.entity.ClothesType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class VirtualTryOnPromptTest {

    @Test
    @DisplayName("상의 단계는 상의만 입히라는 프롬프트를 만든다")
    void topPrompt() {
        assertThat(VirtualTryOnPrompt.forType(ClothesType.TOP))
            .startsWith("Apply only the top from the product image.");
    }

    @Test
    @DisplayName("하의 단계는 하의만 입히라는 프롬프트를 만든다")
    void bottomPrompt() {
        assertThat(VirtualTryOnPrompt.forType(ClothesType.BOTTOM))
            .startsWith("Apply only the bottoms (pants or skirt) from the product image.");
    }

    @ParameterizedTest
    @EnumSource(ClothesType.class)
    @DisplayName("모든 카테고리에 나머지 옷은 유지하라는 문구가 들어간다")
    void allTypesKeepRestOfOutfit(ClothesType type) {
        assertThat(VirtualTryOnPrompt.forType(type))
            .contains("Keep the rest of the person's outfit and accessories unchanged.");
    }
}
