package com.otboo.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 비밀번호 초기화 요청.
 *
 * <h2>가입 여부를 응답으로 알려주지 않는다</h2>
 * Swagger 는 사용자가 없으면 404 로 적어두었지만 <b>가입 여부와 무관하게 204 를 준다.</b>
 * 404 를 주면 이메일을 하나씩 넣어보는 것만으로 누가 가입돼 있는지 명단을 만들 수 있다.
 * 로그인이 실패 사유를 나누지 않는 것({@code AuthErrorCode.INVALID_CREDENTIALS})과 같은 이유다.
 * 한쪽만 막으면 막은 쪽이 무의미해진다.
 */
public record ResetPasswordRequest(

        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "유효하지 않은 이메일입니다.")
        @Size(max = 320, message = "이메일이 너무 깁니다.")
        String email
) {

    /** {@code UserCreateRequest} 와 같은 기준으로 다듬는다. 대소문자만 다른 주소는 같은 계정이다. */
    public ResetPasswordRequest {
        email = email == null ? null : email.trim().toLowerCase();
    }
}
