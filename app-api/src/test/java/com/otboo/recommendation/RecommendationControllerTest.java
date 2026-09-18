package com.otboo.recommendation;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.otboo.common.security.AuthPrincipal;
import com.otboo.config.SecurityErrorResponder;
import com.otboo.common.exception.GlobalExceptionHandler;
import com.otboo.common.security.JwtProvider;
import com.otboo.common.storage.StorageProperties;
import com.otboo.recommendation.ai.AiRecommendationService;
import com.otboo.recommendation.ai.RecommendationAiRequest;
import com.otboo.user.entity.Role;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RecommendationController.class)
@Import({SecurityErrorResponder.class, GlobalExceptionHandler.class,
        RecommendationControllerTest.TestSecurityConfig.class,
        RecommendationControllerTest.TestStorageConfig.class})
@TestPropertySource(properties = "otboo.cors.allowed-origins=http://localhost")
class RecommendationControllerTest {

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class TestStorageConfig {
        @org.springframework.context.annotation.Bean
        StorageProperties storageProperties() {
            return new StorageProperties("./data/images", "/images", 5L * 1024 * 1024);
        }
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean RecommendationService recommendationService;
    @MockitoBean AiRecommendationService aiRecommendationService;
    @MockitoBean JwtProvider jwtProvider;

    @Test
    void returnsBasicRecommendation() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID weatherId = UUID.randomUUID();
        UUID clothesId = UUID.randomUUID();
        given(recommendationService.find(userId, weatherId)).willReturn(
                new RecommendationDto(weatherId, userId,
                        List.of(new com.otboo.feed.dto.OotdDto(
                                clothesId, "상의", null, "TOP", List.of()))));

        mockMvc.perform(get("/api/recommendations")
                        .param("weatherId", weatherId.toString())
                        .with(authentication(userAuthentication(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weatherId").value(weatherId.toString()))
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.clothes[0].clothesId").value(clothesId.toString()))
                .andExpect(jsonPath("$.clothes[0].type").value("TOP"));
        verifyNoInteractions(aiRecommendationService);
    }

    @Test
    void delegatesAiRequestWithTrimmedPrompt() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID weatherId = UUID.randomUUID();
        var request = new RecommendationAiRequest(weatherId, "데이트룩 추천해줘");
        given(aiRecommendationService.find(userId, request))
                .willReturn(new RecommendationDto(weatherId, userId, List.of()));

        mockMvc.perform(post("/api/recommendations/ai")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"weatherId":"%s","prompt":"  데이트룩 추천해줘  "}
                                """.formatted(weatherId))
                        .with(authentication(userAuthentication(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weatherId").value(weatherId.toString()))
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.clothes").isArray());
        verify(aiRecommendationService).find(userId, request);
        verifyNoInteractions(recommendationService);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsBlankAiPrompt(String prompt) throws Exception {
        assertInvalidAiRequest(new RecommendationAiRequest(UUID.randomUUID(), prompt));
    }

    @Test
    void rejectsAiPromptOver100Characters() throws Exception {
        assertInvalidAiRequest(new RecommendationAiRequest(UUID.randomUUID(), "가".repeat(101)));
    }

    @Test
    void rejectsMissingAiWeatherId() throws Exception {
        assertInvalidAiRequest(new RecommendationAiRequest(null, "추천해줘"));
    }

    @Test
    void rejectsUnauthenticatedAiRequest() throws Exception {
        mockMvc.perform(post("/api/recommendations/ai")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RecommendationAiRequest(UUID.randomUUID(), "추천해줘"))))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(recommendationService, aiRecommendationService);
    }

    private void assertInvalidAiRequest(RecommendationAiRequest request) throws Exception {
        mockMvc.perform(post("/api/recommendations/ai")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(userAuthentication(UUID.randomUUID()))))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(recommendationService, aiRecommendationService);
    }

    private UsernamePasswordAuthenticationToken userAuthentication(UUID userId) {
        AuthPrincipal principal = new AuthPrincipal(userId, "user@otboo.com", Role.USER);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
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
