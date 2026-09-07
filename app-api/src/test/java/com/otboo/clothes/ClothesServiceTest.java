package com.otboo.clothes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.otboo.clothes.dto.ClothesAttributeDto;
import com.otboo.clothes.dto.ClothesCreateRequest;
import com.otboo.clothes.dto.ClothesDto;
import com.otboo.clothes.entity.ClothesAttributeDefinition;
import com.otboo.clothes.entity.ClothesType;
import com.otboo.clothes.exception.ClothesErrorCode;
import com.otboo.clothes.repository.ClothesAttributeDefinitionRepository;
import com.otboo.clothes.repository.ClothesRepository;
import com.otboo.common.exception.BusinessException;
import com.otboo.common.test.IntegrationTestSupport;
import com.otboo.user.entity.User;
import com.otboo.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ClothesServiceTest extends IntegrationTestSupport {

    @Autowired
    ClothesService clothesService;

    @Autowired
    ClothesRepository clothesRepository;

    @Autowired
    ClothesAttributeDefinitionRepository definitionRepository;

    @Autowired
    UserRepository userRepository;

    @BeforeEach
    void setUp() {
        clothesRepository.deleteAll();
        clothesRepository.flush();
        definitionRepository.deleteAll();
        definitionRepository.flush();
        userRepository.deleteAll();
        userRepository.flush();
    }

    @Test
    @DisplayName("로그인한 사용자의 ownerId로 이미지 없이 의상을 등록한다")
    void createsClothesWithoutImage() {
        User owner = saveUser();

        ClothesDto result = clothesService.create(owner.getId(), new ClothesCreateRequest(
                owner.getId(), "  기본 티셔츠  ", ClothesType.TOP, null));

        assertThat(result.id()).isNotNull();
        assertThat(result.ownerId()).isEqualTo(owner.getId());
        assertThat(result.name()).isEqualTo("기본 티셔츠");
        assertThat(result.type()).isEqualTo(ClothesType.TOP);
        assertThat(result.imageUrl()).isNull();
        assertThat(result.attributes()).isEmpty();
    }

    @Test
    @DisplayName("속성 정의의 선택값을 확인한 뒤 의상 속성값을 함께 등록한다")
    void createsClothesWithAttributes() {
        User owner = saveUser();
        ClothesAttributeDefinition definition = definitionRepository.saveAndFlush(
                ClothesAttributeDefinition.create("색상", List.of("블랙", "화이트")));

        ClothesDto result = clothesService.create(owner.getId(), new ClothesCreateRequest(
                owner.getId(), "기본 티셔츠", ClothesType.TOP,
                List.of(new ClothesAttributeDto(definition.getId(), "블랙"))));

        assertThat(result.attributes()).hasSize(1);
        assertThat(result.attributes().get(0).definitionId()).isEqualTo(definition.getId());
        assertThat(result.attributes().get(0).definitionName()).isEqualTo("색상");
        assertThat(result.attributes().get(0).selectableValues())
                .containsExactlyInAnyOrder("블랙", "화이트");
        assertThat(result.attributes().get(0).value()).isEqualTo("블랙");
    }

    @Test
    @DisplayName("요청 ownerId가 로그인 사용자와 다르면 등록할 수 없다")
    void rejectsDifferentOwner() {
        User owner = saveUser();

        assertThatThrownBy(() -> clothesService.create(owner.getId(), new ClothesCreateRequest(
                UUID.randomUUID(), "기본 티셔츠", ClothesType.TOP, null)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ClothesErrorCode.NOT_OWNER));
        assertThat(clothesRepository.count()).isZero();
    }

    @Test
    @DisplayName("존재하지 않는 속성 정의는 등록할 수 없다")
    void rejectsMissingDefinition() {
        User owner = saveUser();

        assertThatThrownBy(() -> clothesService.create(owner.getId(), new ClothesCreateRequest(
                owner.getId(), "기본 티셔츠", ClothesType.TOP,
                List.of(new ClothesAttributeDto(UUID.randomUUID(), "블랙")))))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ClothesErrorCode.ATTRIBUTE_DEFINITION_NOT_FOUND));
        assertThat(clothesRepository.count()).isZero();
    }

    @Test
    @DisplayName("속성 정의에 포함되지 않은 선택값은 등록할 수 없다")
    void rejectsSelectableValueNotInDefinition() {
        User owner = saveUser();
        ClothesAttributeDefinition definition = definitionRepository.saveAndFlush(
                ClothesAttributeDefinition.create("색상", List.of("블랙")));

        assertThatThrownBy(() -> clothesService.create(owner.getId(), new ClothesCreateRequest(
                owner.getId(), "기본 티셔츠", ClothesType.TOP,
                List.of(new ClothesAttributeDto(definition.getId(), "겨울")))))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ClothesErrorCode.INVALID_SELECTABLE_VALUE));
        assertThat(clothesRepository.count()).isZero();
    }

    @Test
    @DisplayName("같은 속성 정의를 요청에 두 번 넣을 수 없다")
    void rejectsDuplicateDefinitionInRequest() {
        User owner = saveUser();
        ClothesAttributeDefinition definition = definitionRepository.saveAndFlush(
                ClothesAttributeDefinition.create("색상", List.of("블랙", "화이트")));

        assertThatThrownBy(() -> clothesService.create(owner.getId(), new ClothesCreateRequest(
                owner.getId(), "기본 티셔츠", ClothesType.TOP,
                List.of(
                        new ClothesAttributeDto(definition.getId(), "블랙"),
                        new ClothesAttributeDto(definition.getId(), "화이트")
                ))))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ClothesErrorCode.DUPLICATE_CLOTHES_ATTRIBUTE));
        assertThat(clothesRepository.count()).isZero();
    }

    private User saveUser() {
        return userRepository.saveAndFlush(
                User.create("owner-%s@otboo.com".formatted(UUID.randomUUID()), "encoded", "소유자"));
    }
}
