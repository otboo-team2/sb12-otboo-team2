package com.otboo.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 로그인 요청. Swagger 상 {@code multipart/form-data} 이므로 {@code @RequestBody} 로 받으면 415 가 난다.
 *
 * @param username 이메일
 */
public record SignInRequest(
        @NotBlank String username,
        @NotBlank String password
) {
}
