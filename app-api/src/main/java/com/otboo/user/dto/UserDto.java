package com.otboo.user.dto;

import com.otboo.user.entity.Role;
import com.otboo.user.entity.User;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 계정 응답. 필드명이 프론트 {@code UserDto} 와 일치해야 한다.
 *
 * <p>{@code linkedOAuthProviders} 는 Swagger 에는 없지만 프론트가 실제로 쓰는 필드다.
 */
public record UserDto(
        UUID id,
        Instant createdAt,
        String email,
        String name,
        Role role,
        List<String> linkedOAuthProviders,
        boolean locked
) {

    public static UserDto from(User user, List<String> linkedOAuthProviders) {
        return new UserDto(
                user.getId(), user.getCreatedAt(), user.getEmail(), user.getName(),
                user.getRole(), linkedOAuthProviders, user.isLocked()
        );
    }

    public static UserDto from(User user) {
        return from(user, List.of());
    }
}
