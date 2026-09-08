package com.otboo.clothes;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.clothes.dto.ClothesDto;
import com.otboo.clothes.entity.Clothes;
import com.otboo.clothes.entity.ClothesAttributeDefinition;
import com.otboo.clothes.entity.ClothesType;
import com.otboo.clothes.repository.ClothesAttributeDefinitionRepository;
import com.otboo.clothes.repository.ClothesRepository;
import com.otboo.common.pagination.CursorRequest;
import com.otboo.common.pagination.CursorResponse;
import com.otboo.common.pagination.SortDirection;
import com.otboo.common.test.IntegrationTestSupport;
import com.otboo.user.entity.User;
import com.otboo.user.repository.UserRepository;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ClothesListServiceTest extends IntegrationTestSupport {

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
    @DisplayName("소유자의 의상을 ID 내림차순 커서로 페이지 조회한다")
    void listsByIdCursor() {
        User owner = saveUser();
        Clothes first = saveClothes(owner, "첫 번째", ClothesType.TOP);
        Clothes second = saveClothes(owner, "두 번째", ClothesType.TOP);
        Clothes third = saveClothes(owner, "세 번째", ClothesType.OUTER);
        List<UUID> expectedIds = List.of(first.getId(), second.getId(), third.getId()).stream()
                .sorted(Comparator.comparing(UUID::toString).reversed())
                .toList();

        CursorResponse<ClothesDto> firstPage = clothesService.findAll(
                owner.getId(),
                null,
                new CursorRequest(null, null, 2, "id", SortDirection.DESCENDING));

        assertThat(firstPage.data()).extracting(ClothesDto::id)
                .containsExactlyElementsOf(expectedIds.subList(0, 2));
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(firstPage.totalCount()).isEqualTo(3);

        CursorResponse<ClothesDto> secondPage = clothesService.findAll(
                owner.getId(),
                null,
                new CursorRequest(
                        firstPage.nextCursor(),
                        firstPage.nextIdAfter(),
                        2,
                        "id",
                        SortDirection.DESCENDING));

        assertThat(secondPage.data()).extracting(ClothesDto::id)
                .containsExactly(expectedIds.get(2));
        assertThat(secondPage.hasNext()).isFalse();
    }

    @Test
    @DisplayName("소유자와 의상 타입 조건을 함께 적용한다")
    void filtersByOwnerAndType() {
        User owner = saveUser();
        User anotherOwner = saveUser();
        saveClothes(owner, "상의", ClothesType.TOP);
        saveClothes(owner, "아우터", ClothesType.OUTER);
        saveClothes(anotherOwner, "다른 사용자 상의", ClothesType.TOP);

        CursorResponse<ClothesDto> response = clothesService.findAll(
                owner.getId(),
                ClothesType.TOP,
                new CursorRequest(null, null, 20, "id", SortDirection.DESCENDING));

        assertThat(response.data()).hasSize(1);
        assertThat(response.data().get(0).ownerId()).isEqualTo(owner.getId());
        assertThat(response.data().get(0).type()).isEqualTo(ClothesType.TOP);
        assertThat(response.totalCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("목록 응답에 의상 속성 정의와 선택 가능한 값이 포함된다")
    void includesAttributesInResponse() {
        User owner = saveUser();
        ClothesAttributeDefinition definition = definitionRepository.saveAndFlush(
                ClothesAttributeDefinition.create("색상", List.of("블랙", "화이트")));
        Clothes clothes = Clothes.create(owner.getId(), "티셔츠", ClothesType.TOP, null);
        clothes.addAttribute(definition.getId(), definition.getSelectableValues().get(0).getId());
        clothesRepository.saveAndFlush(clothes);

        CursorResponse<ClothesDto> response = clothesService.findAll(
                owner.getId(),
                null,
                new CursorRequest(null, null, 20, "id", SortDirection.DESCENDING));

        assertThat(response.data().get(0).attributes()).hasSize(1);
        assertThat(response.data().get(0).attributes().get(0).definitionName())
                .isEqualTo("색상");
        assertThat(response.data().get(0).attributes().get(0).selectableValues())
                .containsExactlyInAnyOrder("블랙", "화이트");
        assertThat(response.data().get(0).attributes().get(0).value()).isEqualTo("블랙");
    }

    @Test
    @DisplayName("조건에 맞는 의상이 없으면 빈 페이지를 반환한다")
    void returnsEmptyPage() {
        User owner = saveUser();

        CursorResponse<ClothesDto> response = clothesService.findAll(
                owner.getId(),
                ClothesType.TOP,
                new CursorRequest(null, null, 20, "id", SortDirection.DESCENDING));

        assertThat(response.data()).isEmpty();
        assertThat(response.hasNext()).isFalse();
        assertThat(response.totalCount()).isZero();
    }

    private Clothes saveClothes(User owner, String name, ClothesType type) {
        return clothesRepository.saveAndFlush(Clothes.create(owner.getId(), name, type, null));
    }

    private User saveUser() {
        return userRepository.saveAndFlush(
                User.create("owner-%s@otboo.com".formatted(UUID.randomUUID()), "encoded", "소유자"));
    }
}
