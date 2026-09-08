package com.otboo.user.dto;

import jakarta.validation.constraints.NotNull;

/** {@code Boolean} 이어야 한다. {@code boolean} 이면 값을 안 보낸 요청이 조용히 {@code false} 가 된다. */
public record UserLockUpdateRequest(

        @NotNull(message = "변경할 잠금 상태를 지정해주세요.")
        Boolean locked
) {
}
