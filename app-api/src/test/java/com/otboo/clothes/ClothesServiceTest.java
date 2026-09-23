package com.otboo.clothes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
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
import com.otboo.clothes.repository.ClothesAttributeValueRepository;
import com.otboo.clothes.repository.ClothesRepository;
import com.otboo.common.exception.BusinessException;
import com.otboo.common.storage.ImageStorage;
import com.otboo.common.test.IntegrationTestSupport;
import com.otboo.user.entity.User;
import com.otboo.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Transactional
class ClothesServiceTest extends IntegrationTestSupport {

    @Autowired
    ClothesService clothesService;

    @Autowired
    ClothesRepository clothesRepository;

    @Autowired
    ClothesAttributeValueRepository attributeValueRepository;

    @Autowired
    ClothesAttributeDefinitionRepository definitionRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    EntityManager entityManager;

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
    @DisplayName("이미지가 있는 의상을 등록하면 공통 이미지 저장소 URL을 저장한다")
    void createsClothesWithImage() {
        User owner = saveUser();
        MockMultipartFile image = new MockMultipartFile(
                "image", "shirt.jpg", "image/jpeg", "image".getBytes());
        given(imageStorage.store(any(MultipartFile.class), eq("clothes")))
                .willReturn("/images/clothes/shirt.jpg");

        ClothesDto result = clothesService.create(owner.getId(), new ClothesCreateRequest(
                owner.getId(), "이미지 티셔츠", ClothesType.TOP, null), image);

        assertThat(result.imageUrl()).isEqualTo("/images/clothes/shirt.jpg");
        verify(imageStorage).store(image, "clothes");
    }

    @Test
    @DisplayName("의상 수정에서 이미지 파트를 생략하면 기존 이미지를 유지한다")
    void keepsClothesImageWhenImageIsOmitted() {
        User owner = saveUser();
        Clothes clothes = clothesRepository.saveAndFlush(
                Clothes.create(owner.getId(), "티셔츠", ClothesType.TOP,
                        "/images/clothes/old.jpg"));

        ClothesDto result = clothesService.update(
                owner.getId(), clothes.getId(), new ClothesUpdateRequest("새 이름", null, null));

        assertThat(result.imageUrl()).isEqualTo("/images/clothes/old.jpg");
        verify(imageStorage, never()).store(any(MultipartFile.class), eq("clothes"));
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

    @Test
    @DisplayName("본인 소유의 의상을 삭제한다")
    void deletesOwnedClothes() {
        User owner = saveUser();
        ClothesDto created = clothesService.create(owner.getId(), new ClothesCreateRequest(
                owner.getId(), "삭제할 티셔츠", ClothesType.TOP, null));

        clothesService.delete(owner.getId(), created.id());

        entityManager.clear();
        assertThat(clothesRepository.findById(created.id())).isEmpty();
    }

    @Test
    @DisplayName("의상을 삭제하면 연결된 속성값도 함께 삭제한다")
    void deletesClothesAttributesWithClothes() {
        User owner = saveUser();
        ClothesAttributeDefinition definition = definitionRepository.saveAndFlush(
                ClothesAttributeDefinition.create("색상", List.of("블랙")));
        ClothesDto created = clothesService.create(owner.getId(), new ClothesCreateRequest(
                owner.getId(), "속성이 있는 티셔츠", ClothesType.TOP,
                List.of(new ClothesAttributeDto(definition.getId(), "블랙"))));

        assertThat(attributeValueRepository.count()).isEqualTo(1);

        clothesService.delete(owner.getId(), created.id());

        entityManager.clear();
        assertThat(attributeValueRepository.count()).isZero();
    }

    @Test
    @DisplayName("의상을 삭제하면 기존 이미지도 공통 저장소에서 삭제한다")
    void deletesClothesImageAfterDeletingClothes() {
        User owner = saveUser();
        Clothes clothes = clothesRepository.saveAndFlush(
                Clothes.create(owner.getId(), "이미지 티셔츠", ClothesType.TOP,
                        "/images/clothes/shirt.jpg"));

        clothesService.delete(owner.getId(), clothes.getId());

        verify(imageStorage).delete("/images/clothes/shirt.jpg");
    }

    @Test
    @DisplayName("존재하지 않는 의상을 삭제하면 찾을 수 없음 오류를 준다")
    void rejectsDeleteOfMissingClothes() {
        assertThatThrownBy(() -> clothesService.delete(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ClothesErrorCode.CLOTHES_NOT_FOUND));
    }

    @Test
    @DisplayName("다른 사용자의 의상은 삭제할 수 없다")
    void rejectsDeleteOfAnotherUsersClothes() {
        User owner = saveUser();
        User otherUser = saveUser();
        ClothesDto created = clothesService.create(owner.getId(), new ClothesCreateRequest(
                owner.getId(), "다른 사용자가 삭제할 수 없는 옷", ClothesType.TOP, null));

        assertThatThrownBy(() -> clothesService.delete(otherUser.getId(), created.id()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ClothesErrorCode.NOT_OWNER));
        assertThat(clothesRepository.findById(created.id())).isPresent();
    }

    @Test
    @DisplayName("추천 이력에서 사용 중인 의상은 삭제할 수 없다")
    void rejectsDeleteOfClothesReferencedByRecommendation() {
        User owner = saveUser();
        ClothesDto created = clothesService.create(owner.getId(), new ClothesCreateRequest(
                owner.getId(), "추천에 사용 중인 옷", ClothesType.TOP, null));
        UUID recommendationId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO recommendation_histories
                    (id, user_id, weather_signature, created_at, updated_at)
                VALUES (?, ?, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, recommendationId.toString(), owner.getId().toString(), "test");
        jdbcTemplate.update("""
                INSERT INTO recommendation_items
                    (id, recommendation_id, category, clothes_id, order_index,
                     created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, UUID.randomUUID().toString(), recommendationId.toString(), "TOP",
                created.id().toString(), 0);

        assertThatThrownBy(() -> clothesService.delete(owner.getId(), created.id()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ClothesErrorCode.CLOTHES_IN_USE));
    }

    private User saveUser() {
        return userRepository.saveAndFlush(
                User.create("owner-%s@otboo.com".formatted(UUID.randomUUID()), "encoded", "소유자"));
    }
}
