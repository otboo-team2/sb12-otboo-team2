package com.otboo.clothes.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.otboo.clothes.exception.ClothesErrorCode;
import com.otboo.common.exception.BusinessException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ClothesTest {

    @Test
    @DisplayName("이미지가 없어도 의상을 생성할 수 있다")
    void createsWithoutImage() {
        UUID ownerId = UUID.randomUUID();

        Clothes clothes = Clothes.create(ownerId, "기본 티셔츠", ClothesType.TOP, null);

        assertThat(clothes.getOwnerId()).isEqualTo(ownerId);
        assertThat(clothes.getName()).isEqualTo("기본 티셔츠");
        assertThat(clothes.getType()).isEqualTo(ClothesType.TOP);
        assertThat(clothes.getImageUrl()).isNull();
        assertThat(clothes.isFavorite()).isFalse();
        assertThat(clothes.getAttributes()).isEmpty();
    }

    @Test
    @DisplayName("같은 속성 정의를 한 의상에 두 번 추가할 수 없다")
    void rejectsDuplicateDefinition() {
        Clothes clothes = Clothes.create(
                UUID.randomUUID(), "기본 티셔츠", ClothesType.TOP, null);
        UUID definitionId = UUID.randomUUID();

        clothes.addAttribute(definitionId, UUID.randomUUID());

        assertThatThrownBy(() -> clothes.addAttribute(definitionId, UUID.randomUUID()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ClothesErrorCode.DUPLICATE_CLOTHES_ATTRIBUTE));
    }
}
