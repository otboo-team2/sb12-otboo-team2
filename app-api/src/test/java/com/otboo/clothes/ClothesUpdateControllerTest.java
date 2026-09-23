package com.otboo.clothes;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.clothes.entity.Clothes;
import com.otboo.clothes.entity.ClothesType;
import com.otboo.clothes.repository.ClothesRepository;
import com.otboo.common.storage.ImageStorage;
import com.otboo.common.security.AuthPrincipal;
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
class ClothesUpdateControllerTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

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
    @DisplayName("의상 수정 API는 요청한 필드만 수정하고 200을 반환한다")
    void updatesClothes() throws Exception {
        User owner = saveUser();
        Clothes clothes = clothesRepository.saveAndFlush(
                Clothes.create(owner.getId(), "기존 이름", ClothesType.TOP, null));

        mockMvc.perform(multipart("/api/clothes/{clothesId}", clothes.getId())
                        .file(requestPart("{\"name\":\"새 이름\"}"))
                        .with(authentication(ownerAuthentication(owner.getId())))
                        .with(csrf())
                        .with(request -> {
                            request.setMethod("PATCH");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(clothes.getId().toString()))
                .andExpect(jsonPath("$.name").value("새 이름"))
                .andExpect(jsonPath("$.type").value("TOP"));
    }

    @Test
    @DisplayName("변경 내용이 없는 수정 요청은 400을 반환한다")
    void rejectsEmptyUpdate() throws Exception {
        User owner = saveUser();
        Clothes clothes = clothesRepository.saveAndFlush(
                Clothes.create(owner.getId(), "기존 이름", ClothesType.TOP, null));

        mockMvc.perform(multipart("/api/clothes/{clothesId}", clothes.getId())
                        .file(requestPart("{}"))
                        .with(authentication(ownerAuthentication(owner.getId())))
                        .with(csrf())
                        .with(request -> {
                            request.setMethod("PATCH");
                            return request;
                        }))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exceptionName").value("CLOTHES_007"));
    }

    @Test
    @DisplayName("의상 수정 API는 이미지 파트만 보내도 새 이미지로 교체한다")
    void updatesImageOnly() throws Exception {
        User owner = saveUser();
        Clothes clothes = clothesRepository.saveAndFlush(
                Clothes.create(owner.getId(), "기존 이름", ClothesType.TOP,
                        "/images/clothes/old.jpg"));
        given(imageStorage.store(any(MultipartFile.class), eq("clothes")))
                .willReturn("/images/clothes/new.jpg");

        mockMvc.perform(multipart("/api/clothes/{clothesId}", clothes.getId())
                        .file(requestPart("{}"))
                        .file(new MockMultipartFile(
                                "image", "new.jpg", MediaType.IMAGE_JPEG_VALUE,
                                "image".getBytes(StandardCharsets.UTF_8)))
                        .with(authentication(ownerAuthentication(owner.getId())))
                        .with(csrf())
                        .with(request -> {
                            request.setMethod("PATCH");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value("/images/clothes/new.jpg"));
    }

    private MockMultipartFile requestPart(
            String json
    ) {
        return new MockMultipartFile(
                "request",
                "request.json",
                MediaType.APPLICATION_JSON_VALUE,
                json.getBytes(StandardCharsets.UTF_8));
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
