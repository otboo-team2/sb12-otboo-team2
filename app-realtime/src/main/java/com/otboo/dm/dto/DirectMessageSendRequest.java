package com.otboo.dm.dto;

import java.util.UUID;

public record DirectMessageSendRequest(
    UUID receiverId,
    String content
) {

}
