package com.otboo.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 에러 응답 형식이 Swagger 스펙(ErrorResponse)과 어긋나지 않는지 지키는 테스트.
 * 형식이 깨지면 프론트가 전부 깨지므로 여기서 막는다.
 */
class GlobalExceptionHandlerTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new TestController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    @DisplayName("BusinessException 은 코드·메시지·details 를 담아 지정된 상태로 응답한다")
    void businessException() throws Exception {
        mockMvc.perform(get("/test/business"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.exceptionName").value("COMMON_200"))
                .andExpect(jsonPath("$.message").value("요청한 리소스를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.details.resourceId").value("abc"));
    }

    @Test
    @DisplayName("필수 파라미터 누락은 COMMON_002 로 변환된다")
    void missingParameter() throws Exception {
        mockMvc.perform(get("/test/param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exceptionName").value("COMMON_002"))
                .andExpect(jsonPath("$.details.parameter").value("limit"));
    }

    @Test
    @DisplayName("파라미터 타입 불일치는 COMMON_003 으로 변환된다")
    void typeMismatch() throws Exception {
        mockMvc.perform(get("/test/param").param("limit", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exceptionName").value("COMMON_003"));
    }

    @Test
    @DisplayName("예상 못한 예외는 내부 메시지를 노출하지 않는다")
    void unexpectedException() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.exceptionName").value("COMMON_900"))
                .andExpect(jsonPath("$.message").value("서버 오류가 발생했습니다."));
    }

    @RestController
    static class TestController {

        @GetMapping("/test/business")
        void business() {
            throw new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND)
                    .addDetail("resourceId", "abc");
        }

        @GetMapping("/test/param")
        void param(@RequestParam int limit) {
        }

        @GetMapping("/test/boom")
        void boom() {
            throw new IllegalStateException("DB 커넥션 정보가 담긴 내부 메시지");
        }
    }
}
