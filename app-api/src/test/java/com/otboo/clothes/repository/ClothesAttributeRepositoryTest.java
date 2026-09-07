package com.otboo.clothes.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.otboo.clothes.entity.ClothesAttributeDefinition;
import com.otboo.common.test.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ClothesAttributeRepositoryTest extends IntegrationTestSupport {

    @Autowired
    ClothesAttributeDefinitionRepository definitionRepository;

    @Autowired
    ClothesAttributeSelectableValueRepository selectableValueRepository;

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
    @DisplayName("속성 정의를 저장하면 선택값과 UUID 및 감사 시각이 함께 저장된다")
    void savesDefinitionWithSelectableValues() {
        ClothesAttributeDefinition saved = definitionRepository.saveAndFlush(
                ClothesAttributeDefinition.create("색상", List.of("블랙", "화이트")));
        entityManager.clear();

        ClothesAttributeDefinition found = definitionRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getId()).isNotNull();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
        assertThat(found.getSelectableValues())
                .allSatisfy(value -> {
                    assertThat(value.getId()).isNotNull();
                    assertThat(value.getDefinition().getId()).isEqualTo(found.getId());
                })
                .extracting("value")
                .containsExactlyInAnyOrder("블랙", "화이트");
    }

    @Test
    @DisplayName("속성 이름은 대소문자를 무시해 존재 여부를 조회한다")
    void checksDefinitionNameIgnoringCase() {
        ClothesAttributeDefinition saved = definitionRepository.saveAndFlush(
                ClothesAttributeDefinition.create("Color", List.of("Black")));

        assertThat(definitionRepository.existsByNameIgnoreCase("color")).isTrue();
        assertThat(definitionRepository.existsByNameIgnoreCaseAndIdNot("COLOR", saved.getId()))
                .isFalse();
    }

    @Test
    @DisplayName("DB는 대소문자만 다른 속성 이름의 중복 저장을 거부한다")
    void databaseRejectsDuplicateDefinitionNameIgnoringCase() {
        definitionRepository.saveAndFlush(
                ClothesAttributeDefinition.create("Color", List.of("Black")));

        assertThatThrownBy(() -> definitionRepository.saveAndFlush(
                ClothesAttributeDefinition.create("color", List.of("White"))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("DB는 같은 정의 안에서 대소문자만 다른 선택값의 중복 저장을 거부한다")
    void databaseRejectsDuplicateSelectableValueIgnoringCase() {
        ClothesAttributeDefinition saved = definitionRepository.saveAndFlush(
                ClothesAttributeDefinition.create("색상", List.of("Black")));

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO clothes_attribute_selectable_values
                    (id, definition_id, value, created_at, updated_at)
                VALUES (?, ?, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, UUID.randomUUID().toString(), saved.getId().toString(), "black"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("속성 정의를 삭제하면 소속 선택값도 함께 삭제된다")
    void deletesSelectableValuesWithDefinition() {
        ClothesAttributeDefinition saved = definitionRepository.saveAndFlush(
                ClothesAttributeDefinition.create("색상", List.of("블랙", "화이트")));
        assertThat(selectableValueRepository.count()).isEqualTo(2);

        definitionRepository.delete(saved);
        definitionRepository.flush();

        Long remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM clothes_attribute_selectable_values", Long.class);
        assertThat(remaining).isZero();
    }

    @Test
    @DisplayName("선택값을 교체해 저장해도 유지된 값의 ID는 바뀌지 않는다")
    void keepsExistingSelectableValueIdWhenReplacingValues() {
        ClothesAttributeDefinition saved = definitionRepository.saveAndFlush(
                ClothesAttributeDefinition.create("색상", List.of("Black", "White")));
        entityManager.clear();

        ClothesAttributeDefinition found = definitionRepository.findById(saved.getId()).orElseThrow();
        var existingBlack = found.getSelectableValues().stream()
                .filter(value -> value.getValue().equals("Black"))
                .findFirst()
                .orElseThrow();
        var existingBlackId = existingBlack.getId();

        found.replaceSelectableValues(List.of("black", "Navy"));
        definitionRepository.flush();
        entityManager.clear();

        ClothesAttributeDefinition updated = definitionRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getSelectableValues())
                .extracting("value")
                .containsExactlyInAnyOrder("black", "Navy");
        assertThat(updated.getSelectableValues().stream()
                .filter(value -> value.getValue().equals("black"))
                .findFirst()
                .orElseThrow()
                .getId()).isEqualTo(existingBlackId);
    }
}
