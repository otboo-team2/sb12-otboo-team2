package com.otboo.clothes;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.otboo.clothes.dto.ClothesExtractionDto;
import com.otboo.clothes.dto.ClothesExtractionSource;
import com.otboo.clothes.dto.ExtractedClothesAttributeDto;
import com.otboo.clothes.entity.ClothesType;
import com.otboo.config.SecurityErrorResponder;
import com.otboo.common.exception.GlobalExceptionHandler;
import com.otboo.common.security.AuthPrincipal;
import com.otboo.common.security.JwtProvider;
import com.otboo.common.storage.StorageProperties;
import com.otboo.user.entity.Role;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ClothesExtractionController.class)
@Import({ClothesExtractionController.class, SecurityErrorResponder.class, GlobalExceptionHandler.class,
        ClothesExtractionControllerTest.TestStorageConfig.class,
        ClothesExtractionControllerTest.TestSecurityConfig.class})
@TestPropertySource(properties =
        "otboo.cors.allowed-origins=http://localhost")
class ClothesExtractionControllerTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class TestStorageConfig {
        @Bean
        StorageProperties storageProperties() {
            return new StorageProperties("./data/images", "/images", 5L * 1024 * 1024);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestSecurityConfig {
        @Bean
        org.springframework.security.web.SecurityFilterChain testFilterChain(
                org.springframework.security.config.annotation.web.builders.HttpSecurity http,
                SecurityErrorResponder errorResponder
        ) throws Exception {
            return http
                    .csrf(csrf -> csrf.disable())
                    .exceptionHandling(handling -> handling
                            .authenticationEntryPoint(errorResponder)
                            .accessDeniedHandler(errorResponder))
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .build();
        }
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ClothesExtractionService clothesExtractionService;

    @MockitoBean
    JwtProvider jwtProvider;

    @Test
    void returnsExtractionResultForAuthenticatedUser() throws Exception {
        UUID definitionId = UUID.randomUUID();
        given(clothesExtractionService.extract(eq("https://shop.example.com/products/1")))
                .willReturn(new ClothesExtractionDto(
                        "세미 와이드 데님",
                        ClothesType.BOTTOM,
                        List.of(new ExtractedClothesAttributeDto(
                                definitionId,
                                "핏",
                                "세미와이드",
                                "세미 와이드 핏",
                                ClothesExtractionSource.PAGE_TEXT)),
                        "https://cdn.example.com/main.jpg",
                        List.of()));

        mockMvc.perform(get("/api/clothes/extractions")
                        .param("url", "https://shop.example.com/products/1")
                        .with(authentication(userAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("세미 와이드 데님"))
                .andExpect(jsonPath("$.type").value("BOTTOM"))
                .andExpect(jsonPath("$.attributes[0].definitionName").value("핏"))
                .andExpect(jsonPath("$.failures").isArray());
    }

    @Test
    void rejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/clothes/extractions")
                        .param("url", "https://shop.example.com/products/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.exceptionName").value("COMMON_100"));

        verifyNoInteractions(clothesExtractionService);
    }

    @Test
    void usesSharedMissingParameterResponse() throws Exception {
        mockMvc.perform(get("/api/clothes/extractions")
                        .with(authentication(userAuthentication())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exceptionName").value("COMMON_002"));

        verifyNoInteractions(clothesExtractionService);
    }

    private UsernamePasswordAuthenticationToken userAuthentication() {
        UUID userId = UUID.randomUUID();
        AuthPrincipal principal = new AuthPrincipal(userId, "user@otboo.com", Role.USER);
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
