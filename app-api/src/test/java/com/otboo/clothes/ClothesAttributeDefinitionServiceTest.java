package com.otboo.clothes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.otboo.clothes.dto.ClothesAttributeDefUpdateRequest;
import com.otboo.clothes.entity.ClothesAttributeDefinition;
import com.otboo.clothes.exception.ClothesErrorCode;
import com.otboo.clothes.repository.ClothesAttributeDefinitionRepository;
import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.CommonErrorCode;
import com.otboo.common.pagination.CursorRequest;
import com.otboo.common.pagination.CursorResponse;
import com.otboo.common.pagination.SortDirection;
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

    @Test
    @DisplayName("속성 정의 목록을 이름 오름차순 커서로 조회한다")
    void listsDefinitionsByNameCursor() {
        var color = definitionRepository.saveAndFlush(
                ClothesAttributeDefinition.create("Color", List.of("Black", "White")));
        var material = definitionRepository.saveAndFlush(
                ClothesAttributeDefinition.create("Material", List.of("Cotton")));
        definitionRepository.saveAndFlush(
                ClothesAttributeDefinition.create("Size", List.of("M", "L")));

        CursorResponse<com.otboo.clothes.dto.ClothesAttributeDefDto> firstPage =
                service.findAll(new CursorRequest(null, null, 2, "name", SortDirection.ASCENDING), null);

        assertThat(firstPage.data()).extracting("name")
                .containsExactly("Color", "Material");
        assertThat(firstPage.data().get(0).selectableValues())
                .containsExactly("Black", "White");
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(firstPage.nextCursor()).isEqualTo("Material");
        assertThat(firstPage.nextIdAfter()).isEqualTo(material.getId());

        CursorResponse<com.otboo.clothes.dto.ClothesAttributeDefDto> secondPage =
                service.findAll(new CursorRequest(
                        firstPage.nextCursor(), firstPage.nextIdAfter(), 2,
                        "name", SortDirection.ASCENDING), null);

        assertThat(secondPage.data()).extracting("name").containsExactly("Size");
        assertThat(secondPage.hasNext()).isFalse();
        assertThat(secondPage.totalCount()).isEqualTo(3);
        assertThat(color.getId()).isNotEqualTo(secondPage.data().get(0).id());
    }

    @Test
    @DisplayName("속성 정의 목록은 이름 부분 검색 결과만 반환한다")
    void searchesDefinitionsByName() {
        definitionRepository.saveAndFlush(
                ClothesAttributeDefinition.create("Color", List.of("Black")));
        definitionRepository.saveAndFlush(
                ClothesAttributeDefinition.create("Material", List.of("Cotton")));
        definitionRepository.saveAndFlush(
                ClothesAttributeDefinition.create("Size", List.of("M")));

        CursorResponse<com.otboo.clothes.dto.ClothesAttributeDefDto> response =
                service.findAll(new CursorRequest(null, null, 20, "name", SortDirection.ASCENDING), "olo");

        assertThat(response.data()).extracting("name").containsExactly("Color");
        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    @DisplayName("속성 정의 목록을 이름 내림차순으로 조회한다")
    void listsDefinitionsByNameDescending() {
        definitionRepository.saveAndFlush(
                ClothesAttributeDefinition.create("Color", List.of("Black")));
        definitionRepository.saveAndFlush(
                ClothesAttributeDefinition.create("Material", List.of("Cotton")));
        definitionRepository.saveAndFlush(
                ClothesAttributeDefinition.create("Size", List.of("M")));

        CursorResponse<com.otboo.clothes.dto.ClothesAttributeDefDto> response =
                service.findAll(new CursorRequest(null, null, 20, "name", SortDirection.DESCENDING), null);

        assertThat(response.data()).extracting("name")
                .containsExactly("Size", "Material", "Color");
        assertThat(response.sortDirection()).isEqualTo(SortDirection.DESCENDING);
    }

    @Test
    @DisplayName("생성일 커서로 페이지를 넘겨도 의상 속성 정의가 중복되지 않는다")
    void listsDefinitionsByCreatedAtCursor() {
        var color = definitionRepository.saveAndFlush(
                ClothesAttributeDefinition.create("Color", List.of("Black")));
        var material = definitionRepository.saveAndFlush(
                ClothesAttributeDefinition.create("Material", List.of("Cotton")));
        var size = definitionRepository.saveAndFlush(
                ClothesAttributeDefinition.create("Size", List.of("M")));

        CursorResponse<com.otboo.clothes.dto.ClothesAttributeDefDto> firstPage =
                service.findAll(new CursorRequest(null, null, 1,
                        "createdAt", SortDirection.ASCENDING), null);
        CursorResponse<com.otboo.clothes.dto.ClothesAttributeDefDto> secondPage =
                service.findAll(new CursorRequest(
                        firstPage.nextCursor(), firstPage.nextIdAfter(), 1,
                        "createdAt", SortDirection.ASCENDING), null);
        CursorResponse<com.otboo.clothes.dto.ClothesAttributeDefDto> thirdPage =
                service.findAll(new CursorRequest(
                        secondPage.nextCursor(), secondPage.nextIdAfter(), 1,
                        "createdAt", SortDirection.ASCENDING), null);

        assertThat(firstPage.data()).hasSize(1);
        assertThat(secondPage.data()).hasSize(1);
        assertThat(thirdPage.data()).hasSize(1);
        assertThat(List.of(
                firstPage.data().get(0).id(),
                secondPage.data().get(0).id(),
                thirdPage.data().get(0).id()))
                .containsExactlyInAnyOrder(color.getId(), material.getId(), size.getId());
        assertThat(thirdPage.hasNext()).isFalse();
        assertThat(thirdPage.sortBy()).isEqualTo("createdAt");
    }

    @Test
    @DisplayName("허용하지 않은 속성 정의 정렬 기준은 거부한다")
    void rejectsUnsupportedSortBy() {
        assertThatThrownBy(() -> service.findAll(
                new CursorRequest(null, null, 20, "id", SortDirection.ASCENDING), null))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ClothesErrorCode.INVALID_ATTRIBUTE_DEFINITION_SORT));
    }

    @Test
    @DisplayName("생성일 정렬의 잘못된 커서는 공통 커서 오류로 거부한다")
    void rejectsInvalidCreatedAtCursor() {
        assertThatThrownBy(() -> service.findAll(
                new CursorRequest("not-an-instant", UUID.randomUUID(), 20,
                        "createdAt", SortDirection.ASCENDING), null))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(CommonErrorCode.INVALID_CURSOR));
    }
}
