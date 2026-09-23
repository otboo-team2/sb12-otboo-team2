package com.otboo.clothes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.otboo.clothes.entity.Clothes;
import com.otboo.clothes.entity.ClothesType;
import com.otboo.clothes.repository.ClothesRepository;
import com.otboo.common.security.AuthPrincipal;
import com.otboo.common.storage.ImageStorage;
import com.otboo.common.test.IntegrationTestSupport;
import com.otboo.user.entity.Role;
import com.otboo.user.entity.User;
import com.otboo.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
class ClothesFavoriteControllerTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ClothesRepository clothesRepository;

    @Autowired
    UserRepository userRepository;

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
    @DisplayName("즐겨찾기 등록 API는 204를 반환한다")
    void addsFavorite() throws Exception {
        User owner = saveUser();
        Clothes clothes = saveClothes(owner, false);

        mockMvc.perform(post("/api/clothes/{clothesId}/favorite", clothes.getId())
                        .with(authentication(ownerAuthentication(owner.getId())))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        clothesRepository.flush();
        assertThat(clothesRepository.findById(clothes.getId()).orElseThrow().isFavorite())
                .isTrue();
    }

    @Test
    @DisplayName("즐겨찾기 취소 API는 204를 반환한다")
    void removesFavorite() throws Exception {
        User owner = saveUser();
        Clothes clothes = saveClothes(owner, true);

        mockMvc.perform(delete("/api/clothes/{clothesId}/favorite", clothes.getId())
                        .with(authentication(ownerAuthentication(owner.getId())))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        clothesRepository.flush();
        assertThat(clothesRepository.findById(clothes.getId()).orElseThrow().isFavorite())
                .isFalse();
    }

    @Test
    @DisplayName("의상 목록 API는 즐겨찾기 필터와 응답 필드를 적용한다")
    void filtersByFavorite() throws Exception {
        User owner = saveUser();
        Clothes favorite = saveClothes(owner, true);
        saveClothes(owner, false);

        mockMvc.perform(get("/api/clothes")
                        .param("ownerId", owner.getId().toString())
                        .param("favorite", "true")
                        .param("limit", "20")
                        .with(authentication(ownerAuthentication(owner.getId())))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(favorite.getId().toString()))
                .andExpect(jsonPath("$.data[0].favorite").value(true));
    }

    private Clothes saveClothes(User owner, boolean favorite) {
        Clothes clothes = Clothes.create(owner.getId(), "티셔츠", ClothesType.TOP, null);
        clothes.changeFavorite(favorite);
        return clothesRepository.saveAndFlush(clothes);
    }

    private UsernamePasswordAuthenticationToken ownerAuthentication(UUID ownerId) {
        AuthPrincipal principal = new AuthPrincipal(ownerId, "owner@otboo.com", Role.USER);
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private User saveUser() {
        return userRepository.saveAndFlush(
                User.create("owner-%s@otboo.com".formatted(UUID.randomUUID()), "encoded", "소유자"));
    }
}
