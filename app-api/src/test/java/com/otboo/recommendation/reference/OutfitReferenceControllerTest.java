package com.otboo.recommendation.reference;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.otboo.common.exception.GlobalExceptionHandler;
import com.otboo.common.security.AuthPrincipal;
import com.otboo.common.security.JwtProvider;
import com.otboo.common.storage.StorageProperties;
import com.otboo.config.SecurityErrorResponder;
import com.otboo.pinterest.tag.SkyTag;
import com.otboo.pinterest.tag.StyleTag;
import com.otboo.pinterest.tag.TempBand;
import com.otboo.user.entity.Role;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OutfitReferenceController.class)
@Import({SecurityErrorResponder.class, GlobalExceptionHandler.class,
        OutfitReferenceControllerTest.TestSecurityConfig.class,
        OutfitReferenceControllerTest.TestStorageConfig.class})
@TestPropertySource(properties = "otboo.cors.allowed-origins=http://localhost")
class OutfitReferenceControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean OutfitReferenceService outfitReferenceService;
    @MockitoBean JwtProvider jwtProvider;

    private final UUID userId = UUID.randomUUID();
    private final UUID weatherId = UUID.randomUUID();

    @Test
    @DisplayName("쉼표로 이은 스타일을 enum 목록으로 받아 넘기고, 원본 핀 주소를 응답에 담는다")
    void bindsStylesAndReturnsReferences() throws Exception {
        given(outfitReferenceService.find(userId, weatherId, List.of(StyleTag.MINIMAL, StyleTag.STREET), 6))
                .willReturn(new OutfitReferencesDto(weatherId, TempBand.T17_19, SkyTag.CLOUDY, List.of(
                        new OutfitReferenceDto("123", "https://i.pinimg.com/a.jpg",
                                "https://www.pinterest.com/pin/123/", null, "니트",
                                List.of(StyleTag.MINIMAL)))));

        mockMvc.perform(get("/api/recommendations/outfit-references")
                        .param("weatherId", weatherId.toString())
                        .param("styles", "MINIMAL,STREET")
                        .param("limit", "6")
                        .with(authentication(user())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tempBand").value("T17_19"))
                .andExpect(jsonPath("$.sky").value("CLOUDY"))
                .andExpect(jsonPath("$.references[0].pinUrl").value("https://www.pinterest.com/pin/123/"))
                .andExpect(jsonPath("$.references[0].styles[0]").value("MINIMAL"));
    }

    @Test
    @DisplayName("스타일·한도 없이도 부를 수 있다")
    void stylesAndLimitAreOptional() throws Exception {
        given(outfitReferenceService.find(userId, weatherId, null, null))
                .willReturn(new OutfitReferencesDto(weatherId, TempBand.T17_19, SkyTag.CLOUDY, List.of()));

        mockMvc.perform(get("/api/recommendations/outfit-references")
                        .param("weatherId", weatherId.toString())
                        .with(authentication(user())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.references").isEmpty());
        verify(outfitReferenceService).find(userId, weatherId, null, null);
    }

    @Test
    @DisplayName("모르는 스타일 값은 400 — 서비스까지 가지 않는다")
    void rejectsUnknownStyle() throws Exception {
        mockMvc.perform(get("/api/recommendations/outfit-references")
                        .param("weatherId", weatherId.toString())
                        .param("styles", "minimalist_street")
                        .with(authentication(user())))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(outfitReferenceService);
    }

    @Test
    @DisplayName("weatherId 가 없으면 400")
    void requiresWeatherId() throws Exception {
        mockMvc.perform(get("/api/recommendations/outfit-references").with(authentication(user())))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(outfitReferenceService);
    }

    @Test
    @DisplayName("로그인하지 않으면 401")
    void rejectsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/recommendations/outfit-references")
                        .param("weatherId", weatherId.toString()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(outfitReferenceService);
    }

    private UsernamePasswordAuthenticationToken user() {
        AuthPrincipal principal = new AuthPrincipal(userId, "user@otboo.com", Role.USER);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class TestStorageConfig {
        @org.springframework.context.annotation.Bean
        StorageProperties storageProperties() {
            return new StorageProperties("./data/images", "/images", 5L * 1024 * 1024);
        }
    }

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class TestSecurityConfig {
        @org.springframework.context.annotation.Bean
        org.springframework.security.web.SecurityFilterChain testFilterChain(
                HttpSecurity http, SecurityErrorResponder errorResponder) throws Exception {
            return http.csrf(csrf -> csrf.disable())
                    .exceptionHandling(handling -> handling
                            .authenticationEntryPoint(errorResponder)
                            .accessDeniedHandler(errorResponder))
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .build();
        }
    }
}
