package com.otboo.clothes;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.otboo.common.security.AuthPrincipal;
import com.otboo.common.test.IntegrationTestSupport;
import com.otboo.clothes.entity.Clothes;
import com.otboo.clothes.entity.ClothesType;
import com.otboo.clothes.repository.ClothesRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
class ClothesListControllerTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ClothesRepository clothesRepository;

    @Autowired
    UserRepository userRepository;

    @BeforeEach
    void setUp() {
        clothesRepository.deleteAll();
        clothesRepository.flush();
        userRepository.deleteAll();
        userRepository.flush();
    }

    @Test
    @DisplayName("의상 목록 API는 커서 응답을 반환한다")
    void listsClothes() throws Exception {
        User owner = saveUser();
        saveClothes(owner, "상의", ClothesType.TOP);
        saveClothes(owner, "아우터", ClothesType.OUTER);

        mockMvc.perform(get("/api/clothes")
                        .param("ownerId", owner.getId().toString())
                        .param("limit", "1")
                        .with(authentication(ownerAuthentication(owner.getId())))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.sortBy").value("id"))
                .andExpect(jsonPath("$.sortDirection").value("DESCENDING"));
    }

    @Test
    @DisplayName("의상 목록 API는 타입 필터를 적용한다")
    void filtersByType() throws Exception {
        User owner = saveUser();
        saveClothes(owner, "상의", ClothesType.TOP);
        saveClothes(owner, "아우터", ClothesType.OUTER);

        mockMvc.perform(get("/api/clothes")
                        .param("ownerId", owner.getId().toString())
                        .param("typeEqual", "TOP")
                        .param("limit", "20")
                        .with(authentication(ownerAuthentication(owner.getId())))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].type").value("TOP"))
                .andExpect(jsonPath("$.totalCount").value(1));
    }

    private Clothes saveClothes(User owner, String name, ClothesType type) {
        return clothesRepository.saveAndFlush(Clothes.create(owner.getId(), name, type, null));
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
