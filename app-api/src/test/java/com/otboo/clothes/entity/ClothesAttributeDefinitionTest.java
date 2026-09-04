package com.otboo.clothes.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.otboo.clothes.exception.ClothesErrorCode;
import com.otboo.common.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ClothesAttributeDefinitionTest {

    @Test
    @DisplayName("속성 정의를 생성하면 이름과 선택값의 앞뒤 공백을 제거한다")
    void trimsNameAndSelectableValues() {
        ClothesAttributeDefinition definition = ClothesAttributeDefinition.create(
                "  색상  ",
                List.of("  블랙", "화이트  ")
        );

        assertThat(definition.getName()).isEqualTo("색상");
        assertThat(definition.getSelectableValues())
                .extracting(ClothesAttributeSelectableValue::getValue)
                .containsExactly("블랙", "화이트");
    }

    @Test
    @DisplayName("공백뿐인 속성 이름은 거부한다")
    void rejectsBlankName() {
        assertThatThrownBy(() -> ClothesAttributeDefinition.create("   ", List.of("블랙")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ClothesErrorCode.INVALID_ATTRIBUTE_NAME));
    }

    @Test
    @DisplayName("속성 이름이 100자를 넘으면 거부한다")
    void rejectsTooLongName() {
        assertThatThrownBy(() -> ClothesAttributeDefinition.create("가".repeat(101), List.of("블랙")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ClothesErrorCode.INVALID_ATTRIBUTE_NAME));
    }

    @Test
    @DisplayName("선택값이 없으면 거부한다")
    void rejectsEmptySelectableValues() {
        assertThatThrownBy(() -> ClothesAttributeDefinition.create("색상", List.of()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ClothesErrorCode.INVALID_SELECTABLE_VALUE));
    }

    @Test
    @DisplayName("공백뿐이거나 100자를 넘는 선택값은 거부한다")
    void rejectsInvalidSelectableValue() {
        assertThatThrownBy(() -> ClothesAttributeDefinition.create("색상", List.of("   ")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ClothesErrorCode.INVALID_SELECTABLE_VALUE));

        assertThatThrownBy(() -> ClothesAttributeDefinition.create("색상", List.of("가".repeat(101))))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ClothesErrorCode.INVALID_SELECTABLE_VALUE));
    }

    @Test
    @DisplayName("공백과 대소문자만 다른 선택값은 같은 요청 안에서 중복으로 거부한다")
    void rejectsDuplicateSelectableValuesIgnoringCaseAndWhitespace() {
        assertThatThrownBy(() -> ClothesAttributeDefinition.create(
                "색상",
                List.of(" Black ", "black")
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ClothesErrorCode.DUPLICATE_SELECTABLE_VALUE_IN_REQUEST));
    }

    @Test
    @DisplayName("이름 변경 시 공백을 제거하고 잘못된 이름이면 기존 값을 유지한다")
    void changesNameSafely() {
        ClothesAttributeDefinition definition = ClothesAttributeDefinition.create(
                "색상", List.of("블랙"));

        definition.changeName("  소재  ");

        assertThat(definition.getName()).isEqualTo("소재");
        assertThatThrownBy(() -> definition.changeName("   "))
                .isInstanceOf(BusinessException.class);
        assertThat(definition.getName()).isEqualTo("소재");
    }

    @Test
    @DisplayName("선택값 교체 시 같은 값의 엔티티는 유지하고 추가·삭제 값만 반영한다")
    void synchronizesSelectableValuesWithoutRecreatingUnchangedValues() {
        ClothesAttributeDefinition definition = ClothesAttributeDefinition.create(
                "색상", List.of("Black", "White"));
        ClothesAttributeSelectableValue existingBlack = definition.getSelectableValues().get(0);

        definition.replaceSelectableValues(List.of("black", "Navy"));

        assertThat(definition.getSelectableValues())
                .extracting(ClothesAttributeSelectableValue::getValue)
                .containsExactly("black", "Navy");
        assertThat(definition.getSelectableValues().get(0)).isSameAs(existingBlack);
    }

    @Test
    @DisplayName("잘못된 교체 목록이면 기존 선택값을 변경하지 않는다")
    void rejectsReplacementBeforeChangingExistingValues() {
        ClothesAttributeDefinition definition = ClothesAttributeDefinition.create(
                "색상", List.of("Black", "White"));

        assertThatThrownBy(() -> definition.replaceSelectableValues(List.of("black", " BLACK ")))
                .isInstanceOf(BusinessException.class);

        assertThat(definition.getSelectableValues())
                .extracting(ClothesAttributeSelectableValue::getValue)
                .containsExactly("Black", "White");
    }

    @Test
    @DisplayName("선택값 컬렉션은 외부에서 직접 변경할 수 없다")
    void preventsDirectMutationOfSelectableValues() {
        ClothesAttributeDefinition definition = ClothesAttributeDefinition.create(
                "색상", List.of("블랙"));

        assertThatThrownBy(() -> definition.getSelectableValues().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(definition.getSelectableValues())
                .extracting(ClothesAttributeSelectableValue::getValue)
                .containsExactly("블랙");
    }
}
