package com.otboo.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 비밀번호 변경. 규칙은 회원가입({@link UserCreateRequest})과 같아야 한다.
 * 가입은 막고 변경은 통과하는 비밀번호가 생기면 안 된다.
 */
public record ChangePasswordRequest(

        @NotBlank(message = "비밀번호를 입력해주세요.")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d@$!%*?&]{6,}$",
                message = "비밀번호는 6자 이상이며 영문과 숫자를 포함해야 합니다.")
        String password
) {
}
