package com.otboo.user.dto;

import com.otboo.user.entity.Role;
import jakarta.validation.constraints.NotNull;

public record UserRoleUpdateRequest(

        @NotNull(message = "변경할 권한을 지정해주세요.")
        Role role
) {
}
