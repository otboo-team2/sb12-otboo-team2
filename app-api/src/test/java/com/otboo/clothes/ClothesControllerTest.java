package com.otboo.clothes;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.clothes.dto.ClothesCreateRequest;
import com.otboo.clothes.entity.Clothes;
import com.otboo.clothes.entity.ClothesType;
import com.otboo.clothes.repository.ClothesRepository;
import com.otboo.common.security.AuthPrincipal;
import com.otboo.common.storage.ImageStorage;
import com.otboo.common.test.IntegrationTestSupport;
import com.otboo.user.entity.Role;
import com.otboo.user.entity.User;
import com.otboo.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import org.springframework.web.multipart.MultipartFile;

@AutoConfigureMockMvc
@Transactional
class ClothesControllerTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    ClothesRepository clothesRepository;

    @MockitoBean
    ImageStorage imageStorage;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        userRepository.flush();
    }

    @Test
    @DisplayName("의상 등록 API는 이미지 없이 201과 의상 정보를 반환한다")
    void createsClothesWithoutImage() throws Exception {
        User owner = saveUser();
        ClothesCreateRequest request = new ClothesCreateRequest(
                owner.getId(), "기본 티셔츠", ClothesType.TOP, List.of());

        mockMvc.perform(multipart("/api/clothes")
                        .file(requestPart(request))
                        .with(authentication(ownerAuthentication(owner.getId())))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.ownerId").value(owner.getId().toString()))
                .andExpect(jsonPath("$.name").value("기본 티셔츠"))
                .andExpect(jsonPath("$.type").value("TOP"))
                .andExpect(jsonPath("$.imageUrl").doesNotExist())
                .andExpect(jsonPath("$.attributes").isArray())
                .andExpect(jsonPath("$.attributes").isEmpty());
    }

    @Test
    @DisplayName("요청 ownerId와 로그인 사용자가 다르면 403을 반환한다")
    void rejectsDifferentOwner() throws Exception {
        User owner = saveUser();
        ClothesCreateRequest request = new ClothesCreateRequest(
                UUID.randomUUID(), "기본 티셔츠", ClothesType.TOP, List.of());

        mockMvc.perform(multipart("/api/clothes")
                        .file(requestPart(request))
                        .with(authentication(ownerAuthentication(owner.getId())))
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.exceptionName").value("CLOTHES_100"));
    }

    @Test
    @DisplayName("의상 삭제 API는 본인 소유 의상을 삭제하고 204를 반환한다")
    void deletesOwnedClothes() throws Exception {
        User owner = saveUser();
        Clothes clothes = clothesRepository.saveAndFlush(
                Clothes.create(owner.getId(), "삭제할 티셔츠", ClothesType.TOP, null));

        mockMvc.perform(delete("/api/clothes/{clothesId}", clothes.getId())
                        .with(authentication(ownerAuthentication(owner.getId())))
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("의상 등록 API는 선택 이미지 파트를 저장하고 URL을 반환한다")
    void createsClothesWithImage() throws Exception {
        User owner = saveUser();
        given(imageStorage.store(any(MultipartFile.class), eq("clothes")))
                .willReturn("/images/clothes/shirt.jpg");
        ClothesCreateRequest request = new ClothesCreateRequest(
                owner.getId(), "이미지 티셔츠", ClothesType.TOP, List.of());

        mockMvc.perform(multipart("/api/clothes")
                        .file(requestPart(request))
                        .file(new MockMultipartFile(
                                "image", "shirt.jpg", MediaType.IMAGE_JPEG_VALUE,
                                "image".getBytes(StandardCharsets.UTF_8)))
                        .with(authentication(ownerAuthentication(owner.getId())))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imageUrl").value("/images/clothes/shirt.jpg"));
    }

    private MockMultipartFile requestPart(ClothesCreateRequest request) throws Exception {
        return new MockMultipartFile(
                "request",
                "request.json",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsString(request).getBytes(StandardCharsets.UTF_8));
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
