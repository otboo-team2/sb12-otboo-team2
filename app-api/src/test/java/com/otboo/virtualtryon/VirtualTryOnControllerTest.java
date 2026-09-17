package com.otboo.virtualtryon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.common.security.AuthPrincipal;
import com.otboo.common.test.IntegrationTestSupport;
import com.otboo.user.entity.Role;
import com.otboo.virtualtryon.dto.VirtualTryOnRequest;
import com.otboo.virtualtryon.entity.VirtualTryOnJob;
import com.otboo.virtualtryon.entity.VirtualTryOnJobStatus;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.multipart.MultipartFile;

@AutoConfigureMockMvc
class VirtualTryOnControllerTest extends IntegrationTestSupport {

    @MockitoBean
    VirtualTryOnService service;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    private VirtualTryOnJob job;
    private UUID userId;

    @BeforeEach
    void setUp() {
        job = mock(VirtualTryOnJob.class);
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("multipart 요청은 로그인 사용자와 의상 ID를 서비스에 전달한다")
    void submitsForAuthenticatedOwner() throws Exception {
        stubPendingJob();
        UUID topId = UUID.randomUUID();
        UUID bottomId = UUID.randomUUID();
        VirtualTryOnRequest request = new VirtualTryOnRequest(topId, bottomId, null);
        MockMultipartFile modelImage = new MockMultipartFile(
                "modelImage", "model.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[]{1, 2});
        given(service.submit(eq(userId), any(VirtualTryOnRequest.class), any(MultipartFile.class)))
                .willReturn(job);

        mockMvc.perform(multipart("/api/fittings")
                        .file(jsonPart(request))
                        .file(modelImage)
                        .with(authentication(ownerAuthentication()))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(job.getId().toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));

        ArgumentCaptor<VirtualTryOnRequest> captured =
                ArgumentCaptor.forClass(VirtualTryOnRequest.class);
        verify(service).submit(eq(userId), captured.capture(), eq(modelImage));
        assertThat(captured.getValue().topClothesId()).isEqualTo(topId);
        assertThat(captured.getValue().bottomClothesId()).isEqualTo(bottomId);
    }

    @Test
    @DisplayName("job 조회는 로그인 사용자 ID를 소유권 검사 인자로 전달한다")
    void getsJobForAuthenticatedOwner() throws Exception {
        stubPendingJob();
        UUID jobId = UUID.randomUUID();
        given(service.findJob(jobId, userId)).willReturn(job);

        mockMvc.perform(get("/api/fittings/{jobId}", jobId)
                        .with(authentication(ownerAuthentication())))
                .andExpect(status().isOk());

        verify(service).findJob(jobId, userId);
    }

    @Test
    @DisplayName("인증 정보가 없으면 서비스 호출 전에 401을 반환한다")
    void rejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/fittings/{jobId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    private MockMultipartFile jsonPart(VirtualTryOnRequest request) throws Exception {
        return new MockMultipartFile(
                "request",
                "request.json",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsString(request).getBytes(StandardCharsets.UTF_8));
    }

    private void stubPendingJob() {
        given(job.getId()).willReturn(UUID.randomUUID());
        given(job.getStatus()).willReturn(VirtualTryOnJobStatus.PENDING);
    }

    private UsernamePasswordAuthenticationToken ownerAuthentication() {
        AuthPrincipal principal = new AuthPrincipal(userId, "owner@otboo.com", Role.USER);
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                java.util.List.of(new SimpleGrantedAuthority(Role.USER.authority())));
    }
}
