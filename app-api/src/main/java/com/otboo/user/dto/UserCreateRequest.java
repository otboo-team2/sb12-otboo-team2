package com.otboo.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 요청.
 *
 * <p>검증 규칙은 프론트({@code RegisterForm.tsx})와 같은 기준이다.
 * 프론트에서 걸러도 서버가 다시 검사해야 한다 — 프론트를 거치지 않는 요청을 막을 수 없다.
 */
public record UserCreateRequest(

        @NotBlank(message = "이름을 입력해주세요.")
        @Pattern(regexp = "^[가-힣a-zA-Z0-9]{2,20}$",
                message = "이름은 2~20자의 한글, 영문, 숫자만 가능합니다.")
        String name,

        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "유효하지 않은 이메일입니다.")
        @Size(max = 320, message = "이메일이 너무 깁니다.")
        String email,

        @NotBlank(message = "비밀번호를 입력해주세요.")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d@$!%*?&]{6,}$",
                message = "비밀번호는 6자 이상이며 영문과 숫자를 포함해야 합니다.")
        String password
) {

    /**
     * 검증보다 먼저 값을 정리한다. 여기서 다듬지 않으면 앞뒤 공백이 붙은 이메일이
     * 형식 오류로 거절돼, 자동완성으로 공백이 섞인 사용자가 이유를 알 수 없게 된다.
     *
     * <p>이메일은 소문자로 낮춘다. 대소문자만 다른 주소가 다른 계정이 되면 안 된다.
     */
    public UserCreateRequest {
        name = name == null ? null : name.trim();
        email = email == null ? null : email.trim().toLowerCase();
    }
}
