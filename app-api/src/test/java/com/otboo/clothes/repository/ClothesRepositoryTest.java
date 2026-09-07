package com.otboo.clothes.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.otboo.clothes.entity.Clothes;
import com.otboo.clothes.entity.ClothesAttributeDefinition;
import com.otboo.clothes.entity.ClothesType;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ClothesRepositoryTest extends IntegrationTestSupport {

    @Autowired
    ClothesRepository clothesRepository;

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
        clothesRepository.deleteAll();
        clothesRepository.flush();
        definitionRepository.deleteAll();
        definitionRepository.flush();
        userRepository.deleteAll();
        userRepository.flush();
    }

    @Test
    @DisplayName("이미지 없이 의상과 감사 필드를 저장한다")
    void savesClothesWithoutImage() {
        User owner = saveUser();

        Clothes saved = clothesRepository.saveAndFlush(
                Clothes.create(owner.getId(), "기본 티셔츠", ClothesType.TOP, null));
        entityManager.clear();

        Clothes found = clothesRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getOwnerId()).isEqualTo(owner.getId());
        assertThat(found.getName()).isEqualTo("기본 티셔츠");
        assertThat(found.getType()).isEqualTo(ClothesType.TOP);
        assertThat(found.getImageUrl()).isNull();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("의상과 속성값을 함께 저장한다")
    void savesClothesAttributes() {
        User owner = saveUser();
        ClothesAttributeDefinition definition = definitionRepository.saveAndFlush(
                ClothesAttributeDefinition.create("색상", List.of("블랙", "화이트")));
        UUID selectableValueId = definition.getSelectableValues().get(0).getId();
        Clothes clothes = Clothes.create(owner.getId(), "기본 티셔츠", ClothesType.TOP, null);
        clothes.addAttribute(definition.getId(), selectableValueId);

        Clothes saved = clothesRepository.saveAndFlush(clothes);
        entityManager.clear();

        Clothes found = clothesRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getAttributes()).hasSize(1);
        assertThat(found.getAttributes().get(0).getDefinitionId()).isEqualTo(definition.getId());
        assertThat(found.getAttributes().get(0).getSelectableValueId()).isEqualTo(selectableValueId);
    }

    @Test
    @DisplayName("DB는 같은 의상에서 같은 속성 정의를 두 번 저장하지 못하게 한다")
    void databaseRejectsDuplicateDefinition() {
        User owner = saveUser();
        ClothesAttributeDefinition definition = definitionRepository.saveAndFlush(
                ClothesAttributeDefinition.create("색상", List.of("블랙", "화이트")));
        Clothes clothes = Clothes.create(owner.getId(), "기본 티셔츠", ClothesType.TOP, null);
        clothesRepository.saveAndFlush(clothes);
        UUID valueId = definition.getSelectableValues().get(0).getId();

        jdbcTemplate.update("""
                INSERT INTO clothes_attribute_values
                    (id, clothes_id, definition_id, selectable_value_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, UUID.randomUUID().toString(), clothes.getId().toString(),
                definition.getId().toString(), valueId.toString());

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO clothes_attribute_values
                    (id, clothes_id, definition_id, selectable_value_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, UUID.randomUUID().toString(), clothes.getId().toString(),
                definition.getId().toString(), valueId.toString()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("DB는 정의와 다른 선택값의 조합을 저장하지 못하게 한다")
    void databaseRejectsSelectableValueFromAnotherDefinition() {
        User owner = saveUser();
        ClothesAttributeDefinition color = definitionRepository.saveAndFlush(
                ClothesAttributeDefinition.create("색상", List.of("블랙")));
        ClothesAttributeDefinition material = definitionRepository.saveAndFlush(
                ClothesAttributeDefinition.create("소재", List.of("면")));
        Clothes clothes = Clothes.create(owner.getId(), "기본 티셔츠", ClothesType.TOP, null);
        clothesRepository.saveAndFlush(clothes);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO clothes_attribute_values
                    (id, clothes_id, definition_id, selectable_value_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, UUID.randomUUID().toString(), clothes.getId().toString(),
                color.getId().toString(),
                material.getSelectableValues().get(0).getId().toString()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("의상을 삭제하면 속성값도 함께 삭제된다")
    void cascadesDeleteToAttributes() {
        User owner = saveUser();
        ClothesAttributeDefinition definition = definitionRepository.saveAndFlush(
                ClothesAttributeDefinition.create("색상", List.of("블랙")));
        Clothes clothes = Clothes.create(owner.getId(), "기본 티셔츠", ClothesType.TOP, null);
        clothes.addAttribute(definition.getId(), definition.getSelectableValues().get(0).getId());
        Clothes saved = clothesRepository.saveAndFlush(clothes);

        clothesRepository.delete(saved);
        clothesRepository.flush();

        Long remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM clothes_attribute_values", Long.class);
        assertThat(remaining).isZero();
    }

    private User saveUser() {
        return userRepository.saveAndFlush(
                User.create("owner-%s@otboo.com".formatted(UUID.randomUUID()), "encoded", "소유자"));
    }
}
