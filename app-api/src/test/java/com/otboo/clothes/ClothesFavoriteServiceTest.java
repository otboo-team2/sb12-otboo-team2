package com.otboo.clothes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.otboo.clothes.dto.ClothesDto;
import com.otboo.clothes.entity.Clothes;
import com.otboo.clothes.entity.ClothesType;
import com.otboo.clothes.exception.ClothesErrorCode;
import com.otboo.clothes.repository.ClothesRepository;
import com.otboo.common.exception.BusinessException;
import com.otboo.common.pagination.CursorRequest;
import com.otboo.common.pagination.CursorResponse;
import com.otboo.common.pagination.SortDirection;
import com.otboo.common.storage.ImageStorage;
import com.otboo.common.test.IntegrationTestSupport;
import com.otboo.user.entity.User;
import com.otboo.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ClothesFavoriteServiceTest extends IntegrationTestSupport {

    @Autowired
    ClothesService clothesService;

    @Autowired
    ClothesRepository clothesRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    EntityManager entityManager;

    @MockitoBean
    ImageStorage imageStorage;

    @BeforeEach
    void setUp() {
        clothesRepository.deleteAll();
        clothesRepository.flush();
        userRepository.deleteAll();
        userRepository.flush();
    }

    @Test
    @DisplayName("본인 소유 의상을 즐겨찾기에 등록하며 같은 요청을 반복해도 유지한다")
    void addsFavoriteIdempotently() {
        User owner = saveUser();
        Clothes clothes = saveClothes(owner, false);

        clothesService.addFavorite(owner.getId(), clothes.getId());
        clothesService.addFavorite(owner.getId(), clothes.getId());

        entityManager.clear();
        assertThat(clothesRepository.findById(clothes.getId()).orElseThrow().isFavorite())
                .isTrue();
    }

    @Test
    @DisplayName("본인 소유 의상의 즐겨찾기를 취소하며 같은 요청을 반복해도 유지한다")
    void removesFavoriteIdempotently() {
        User owner = saveUser();
        Clothes clothes = saveClothes(owner, true);

        clothesService.removeFavorite(owner.getId(), clothes.getId());
        clothesService.removeFavorite(owner.getId(), clothes.getId());

        entityManager.clear();
        assertThat(clothesRepository.findById(clothes.getId()).orElseThrow().isFavorite())
                .isFalse();
    }

    @Test
    @DisplayName("다른 사용자의 의상은 즐겨찾기를 변경할 수 없다")
    void rejectsFavoriteChangeOfAnotherUsersClothes() {
        User owner = saveUser();
        User otherUser = saveUser();
        Clothes clothes = saveClothes(owner, false);

        assertThatThrownBy(() -> clothesService.addFavorite(otherUser.getId(), clothes.getId()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ClothesErrorCode.NOT_OWNER));
    }

    @Test
    @DisplayName("존재하지 않는 의상의 즐겨찾기를 변경할 수 없다")
    void rejectsFavoriteChangeOfMissingClothes() {
        User owner = saveUser();

        assertThatThrownBy(() -> clothesService.addFavorite(owner.getId(), UUID.randomUUID()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ClothesErrorCode.CLOTHES_NOT_FOUND));
    }

    @Test
    @DisplayName("즐겨찾기 조건을 적용하고 응답에 즐겨찾기 상태를 포함한다")
    void filtersByFavorite() {
        User owner = saveUser();
        Clothes favorite = saveClothes(owner, true);
        saveClothes(owner, false);

        CursorResponse<ClothesDto> response = clothesService.findAll(
                owner.getId(),
                null,
                true,
                new CursorRequest(null, null, 20, "id", SortDirection.DESCENDING));

        assertThat(response.data()).hasSize(1);
        assertThat(response.data().get(0).id()).isEqualTo(favorite.getId());
        assertThat(response.data().get(0).favorite()).isTrue();
        assertThat(response.totalCount()).isEqualTo(1);
    }

    private Clothes saveClothes(User owner, boolean favorite) {
        Clothes clothes = Clothes.create(owner.getId(), "티셔츠", ClothesType.TOP, null);
        clothes.changeFavorite(favorite);
        return clothesRepository.saveAndFlush(clothes);
    }

    private User saveUser() {
        return userRepository.saveAndFlush(
                User.create("owner-%s@otboo.com".formatted(UUID.randomUUID()), "encoded", "소유자"));
    }
}
