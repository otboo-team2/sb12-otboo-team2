package com.otboo.dm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record DirectMessageSendRequest(
    @NotNull(message = "받는 사람을 지정해주세요.")
    UUID receiverId,

    @NotBlank(message = "내용을 입력해주세요.")
    @Size(max = 1000, message = "메시지는 1000자를 넘을 수 없습니다.")
    String content
) {
}
