package com.otboo.clothes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.otboo.clothes.dto.ClothesAttributeDto;
import com.otboo.clothes.dto.ClothesCreateRequest;
import com.otboo.clothes.dto.ClothesDto;
import com.otboo.clothes.dto.ClothesUpdateRequest;
import com.otboo.clothes.entity.Clothes;
import com.otboo.clothes.entity.ClothesAttributeDefinition;
import com.otboo.clothes.entity.ClothesType;
import com.otboo.clothes.exception.ClothesErrorCode;
import com.otboo.clothes.repository.ClothesAttributeDefinitionRepository;
import com.otboo.clothes.repository.ClothesRepository;
import com.otboo.common.exception.BusinessException;
import com.otboo.common.storage.ImageStorage;
import com.otboo.common.test.IntegrationTestSupport;
import com.otboo.user.entity.User;
import com.otboo.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Transactional
class ClothesUpdateServiceTest extends IntegrationTestSupport {

    @Autowired
    ClothesService clothesService;

    @Autowired
    ClothesRepository clothesRepository;

    @Autowired
    ClothesAttributeDefinitionRepository definitionRepository;

    @Autowired
    UserRepository userRepository;

    @MockitoBean
    ImageStorage imageStorage;

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
    @DisplayName("이름만 수정하면 타입과 기존 속성은 유지된다")
    void updatesNameOnly() {
        User owner = saveUser();
        ClothesAttributeDefinition definition = saveDefinition();
        Clothes clothes = createClothes(owner, definition, "블랙");

        ClothesDto result = clothesService.update(
                owner.getId(),
                clothes.getId(),
                new ClothesUpdateRequest("새 티셔츠", null, null));

        assertThat(result.name()).isEqualTo("새 티셔츠");
        assertThat(result.type()).isEqualTo(ClothesType.TOP);
        assertThat(result.attributes()).hasSize(1);
        assertThat(result.attributes().get(0).value()).isEqualTo("블랙");
    }

    @Test
    @DisplayName("타입만 수정할 수 있다")
    void updatesTypeOnly() {
        User owner = saveUser();
        Clothes clothes = clothesRepository.saveAndFlush(
                Clothes.create(owner.getId(), "티셔츠", ClothesType.TOP, null));

        ClothesDto result = clothesService.update(
                owner.getId(),
                clothes.getId(),
                new ClothesUpdateRequest(null, ClothesType.OUTER, null));

        assertThat(result.name()).isEqualTo("티셔츠");
        assertThat(result.type()).isEqualTo(ClothesType.OUTER);
    }

    @Test
    @DisplayName("이미지만 보내면 새 이미지로 교체하고 이전 이미지를 삭제한다")
    void replacesImageOnly() {
        User owner = saveUser();
        Clothes clothes = clothesRepository.saveAndFlush(
                Clothes.create(owner.getId(), "티셔츠", ClothesType.TOP,
                        "/images/clothes/old.jpg"));
        MockMultipartFile image = new MockMultipartFile(
                "image", "new.jpg", "image/jpeg", "image".getBytes());
        given(imageStorage.store(any(MultipartFile.class), eq("clothes")))
                .willReturn("/images/clothes/new.jpg");

        ClothesDto result = clothesService.update(
                owner.getId(), clothes.getId(), new ClothesUpdateRequest(null, null, null), image);

        assertThat(result.imageUrl()).isEqualTo("/images/clothes/new.jpg");
        verify(imageStorage).store(image, "clothes");
        verify(imageStorage).delete("/images/clothes/old.jpg");
    }

    @Test
    @DisplayName("속성 목록을 새 목록으로 교체한다")
    void replacesAttributes() {
        User owner = saveUser();
        ClothesAttributeDefinition color = saveDefinition();
        ClothesAttributeDefinition material = definitionRepository.saveAndFlush(
                ClothesAttributeDefinition.create("소재", List.of("면", "울")));
        Clothes clothes = createClothes(owner, color, "블랙");

        ClothesDto result = clothesService.update(
                owner.getId(),
                clothes.getId(),
                new ClothesUpdateRequest(
                        null,
                        null,
                        List.of(new ClothesAttributeDto(material.getId(), "울"))));

        assertThat(result.attributes()).hasSize(1);
        assertThat(result.attributes().get(0).definitionName()).isEqualTo("소재");
        assertThat(result.attributes().get(0).value()).isEqualTo("울");
    }

    @Test
    @DisplayName("빈 속성 배열은 기존 속성을 모두 제거한다")
    void removesAllAttributesWithEmptyList() {
        User owner = saveUser();
        ClothesAttributeDefinition definition = saveDefinition();
        Clothes clothes = createClothes(owner, definition, "블랙");

        ClothesDto result = clothesService.update(
                owner.getId(),
                clothes.getId(),
                new ClothesUpdateRequest(null, null, List.of()));

        assertThat(result.attributes()).isEmpty();
        assertThat(clothesRepository.findById(clothes.getId()).orElseThrow().getAttributes())
                .isEmpty();
    }

    @Test
    @DisplayName("속성 필드를 생략하면 기존 속성을 유지한다")
    void keepsAttributesWhenOmitted() {
        User owner = saveUser();
        ClothesAttributeDefinition definition = saveDefinition();
        Clothes clothes = createClothes(owner, definition, "블랙");

        ClothesDto result = clothesService.update(
                owner.getId(),
                clothes.getId(),
                new ClothesUpdateRequest("새 이름", null, null));

        assertThat(result.attributes()).hasSize(1);
        assertThat(result.attributes().get(0).value()).isEqualTo("블랙");
    }

    @Test
    @DisplayName("변경 필드가 없으면 수정할 수 없다")
    void rejectsEmptyUpdate() {
        User owner = saveUser();
        Clothes clothes = clothesRepository.saveAndFlush(
                Clothes.create(owner.getId(), "티셔츠", ClothesType.TOP, null));

        assertThatThrownBy(() -> clothesService.update(
                owner.getId(),
                clothes.getId(),
                new ClothesUpdateRequest(null, null, null)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ClothesErrorCode.EMPTY_CLOTHES_UPDATE));
    }

    @Test
    @DisplayName("다른 사용자의 의상은 수정할 수 없다")
    void rejectsDifferentOwner() {
        User owner = saveUser();
        User anotherUser = saveUser();
        Clothes clothes = clothesRepository.saveAndFlush(
                Clothes.create(owner.getId(), "티셔츠", ClothesType.TOP, null));

        assertThatThrownBy(() -> clothesService.update(
                anotherUser.getId(),
                clothes.getId(),
                new ClothesUpdateRequest("변경 시도", null, null)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ClothesErrorCode.NOT_OWNER));
    }

    @Test
    @DisplayName("없는 의상을 수정하면 찾을 수 없음 오류를 준다")
    void rejectsMissingClothes() {
        User owner = saveUser();

        assertThatThrownBy(() -> clothesService.update(
                owner.getId(),
                UUID.randomUUID(),
                new ClothesUpdateRequest("변경", null, null)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ClothesErrorCode.CLOTHES_NOT_FOUND));
    }

    @Test
    @DisplayName("잘못된 선택값이면 기존 의상 데이터를 유지한다")
    void keepsExistingDataWhenAttributeIsInvalid() {
        User owner = saveUser();
        ClothesAttributeDefinition definition = saveDefinition();
        Clothes clothes = createClothes(owner, definition, "블랙");

        assertThatThrownBy(() -> clothesService.update(
                owner.getId(),
                clothes.getId(),
                new ClothesUpdateRequest(
                        "변경 시도",
                        ClothesType.OUTER,
                        List.of(new ClothesAttributeDto(definition.getId(), "없는 값")))))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ClothesErrorCode.INVALID_SELECTABLE_VALUE));

        Clothes unchanged = clothesRepository.findById(clothes.getId()).orElseThrow();
        assertThat(unchanged.getName()).isEqualTo("티셔츠");
        assertThat(unchanged.getType()).isEqualTo(ClothesType.TOP);
        assertThat(unchanged.getAttributes()).hasSize(1);
    }

    @Test
    @DisplayName("존재하지 않는 속성 정의는 수정에 사용할 수 없다")
    void rejectsMissingDefinition() {
        User owner = saveUser();
        Clothes clothes = clothesRepository.saveAndFlush(
                Clothes.create(owner.getId(), "티셔츠", ClothesType.TOP, null));

        assertThatThrownBy(() -> clothesService.update(
                owner.getId(),
                clothes.getId(),
                new ClothesUpdateRequest(
                        null,
                        null,
                        List.of(new ClothesAttributeDto(UUID.randomUUID(), "블랙")))))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ClothesErrorCode.ATTRIBUTE_DEFINITION_NOT_FOUND));
    }

    private ClothesAttributeDefinition saveDefinition() {
        return definitionRepository.saveAndFlush(
                ClothesAttributeDefinition.create("색상", List.of("블랙", "화이트")));
    }

    private Clothes createClothes(
            User owner,
            ClothesAttributeDefinition definition,
            String selectedValue
    ) {
        UUID selectedValueId = definition.getSelectableValues().stream()
                .filter(value -> value.getValue().equals(selectedValue))
                .findFirst()
                .orElseThrow()
                .getId();
        Clothes clothes = Clothes.create(owner.getId(), "티셔츠", ClothesType.TOP, null);
        clothes.addAttribute(definition.getId(), selectedValueId);
        return clothesRepository.saveAndFlush(clothes);
    }

    private User saveUser() {
        return userRepository.saveAndFlush(
                User.create("owner-%s@otboo.com".formatted(UUID.randomUUID()), "encoded", "소유자"));
    }
}
