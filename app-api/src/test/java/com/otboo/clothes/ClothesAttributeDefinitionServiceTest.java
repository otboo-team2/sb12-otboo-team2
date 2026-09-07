package com.otboo.clothes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.otboo.clothes.dto.ClothesAttributeDefUpdateRequest;
import com.otboo.clothes.entity.ClothesAttributeDefinition;
import com.otboo.clothes.exception.ClothesErrorCode;
import com.otboo.clothes.repository.ClothesAttributeDefinitionRepository;
import com.otboo.common.exception.BusinessException;
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
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ClothesAttributeDefinitionServiceTest extends IntegrationTestSupport {

    @Autowired
    ClothesAttributeDefinitionService service;

    @Autowired
    ClothesAttributeDefinitionRepository definitionRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    EntityManager entityManager;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        definitionRepository.deleteAll();
        definitionRepository.flush();
    }

    @Test
    @DisplayName("속성 정의를 이름만 수정하면 선택값은 유지된다")
    void updatesNameOnly() {
        var saved = definitionRepository.saveAndFlush(
                ClothesAttributeDefinition.create("색상", List.of("블랙")));

        var result = service.update(saved.getId(),
                new ClothesAttributeDefUpdateRequest("  소재  ", null));

        assertThat(result.name()).isEqualTo("소재");
        assertThat(result.selectableValues()).containsExactly("블랙");
    }

    @Test
    @DisplayName("속성 정의 수정에서 이름과 선택값이 모두 생략되면 거부한다")
    void rejectsEmptyUpdate() {
        var saved = definitionRepository.saveAndFlush(
                ClothesAttributeDefinition.create("색상", List.of("블랙")));

        assertThatThrownBy(() -> service.update(
                saved.getId(), new ClothesAttributeDefUpdateRequest(null, null)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ClothesErrorCode.EMPTY_ATTRIBUTE_UPDATE));
    }

    @Test
    @DisplayName("없는 속성 정의에 빈 수정 요청을 보내면 찾을 수 없음 오류를 우선한다")
    void rejectsEmptyUpdateOfMissingDefinition() {
        assertThatThrownBy(() -> service.update(
                UUID.randomUUID(), new ClothesAttributeDefUpdateRequest(null, null)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ClothesErrorCode.ATTRIBUTE_DEFINITION_NOT_FOUND));
    }

    @Test
    @DisplayName("없는 속성 정의를 수정하면 찾을 수 없음 오류를 준다")
    void rejectsUpdateOfMissingDefinition() {
        assertThatThrownBy(() -> service.update(
                UUID.randomUUID(), new ClothesAttributeDefUpdateRequest("소재", null)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ClothesErrorCode.ATTRIBUTE_DEFINITION_NOT_FOUND));
    }

    @Test
    @DisplayName("사용 중인 선택값을 제거하는 수정은 충돌로 거부한다")
    void rejectsRemovingSelectableValueInUse() {
        var saved = definitionRepository.saveAndFlush(
                ClothesAttributeDefinition.create("색상", List.of("블랙", "화이트")));
        UUID userId = userRepository.save(User.create(
                "clothes-update-owner@otboo.com", "encoded-password", "사용자")).getId();
        userRepository.flush();
        UUID clothesId = UUID.randomUUID();
        UUID valueId = saved.getSelectableValues().get(0).getId();
        jdbcTemplate.update("""
                INSERT INTO clothes
                    (id, owner_id, name, type, image_url, created_at, updated_at)
                VALUES (?, ?, '셔츠', 'TOP', NULL, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, clothesId.toString(), userId.toString());
        jdbcTemplate.update("""
                INSERT INTO clothes_attribute_values
                    (id, clothes_id, definition_id, selectable_value_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, UUID.randomUUID().toString(), clothesId.toString(),
                saved.getId().toString(), valueId.toString());

        assertThatThrownBy(() -> service.update(saved.getId(),
                new ClothesAttributeDefUpdateRequest(null, List.of("화이트"))))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ClothesErrorCode.SELECTABLE_VALUE_IN_USE));
    }

    @Test
    @DisplayName("정의 삭제에 성공하면 204 처리를 할 수 있는 상태가 된다")
    void deletesDefinition() {
        var saved = definitionRepository.saveAndFlush(
                ClothesAttributeDefinition.create("색상", List.of("블랙")));

        service.delete(saved.getId());

        entityManager.clear();
        assertThat(definitionRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    @DisplayName("없는 속성 정의를 삭제하면 찾을 수 없음 오류를 준다")
    void rejectsDeleteOfMissingDefinition() {
        assertThatThrownBy(() -> service.delete(UUID.randomUUID()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ClothesErrorCode.ATTRIBUTE_DEFINITION_NOT_FOUND));
    }

    @Test
    @DisplayName("사용 중인 속성 정의 삭제는 충돌로 거부한다")
    void rejectsDeleteOfDefinitionInUse() {
        var saved = definitionRepository.saveAndFlush(
                ClothesAttributeDefinition.create("색상", List.of("블랙")));
        UUID userId = userRepository.save(User.create(
                "clothes-delete-owner@otboo.com", "encoded-password", "사용자")).getId();
        userRepository.flush();
        UUID clothesId = UUID.randomUUID();
        UUID valueId = saved.getSelectableValues().get(0).getId();
        jdbcTemplate.update("""
                INSERT INTO clothes
                    (id, owner_id, name, type, image_url, created_at, updated_at)
                VALUES (?, ?, '셔츠', 'TOP', NULL, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, clothesId.toString(), userId.toString());
        jdbcTemplate.update("""
                INSERT INTO clothes_attribute_values
                    (id, clothes_id, definition_id, selectable_value_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, UUID.randomUUID().toString(), clothesId.toString(),
                saved.getId().toString(), valueId.toString());

        assertThatThrownBy(() -> service.delete(saved.getId()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ClothesErrorCode.ATTRIBUTE_DEFINITION_IN_USE));
    }
}
