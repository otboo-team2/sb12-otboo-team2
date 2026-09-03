package com.otboo.common.security;

import com.otboo.user.entity.Role;
import java.util.UUID;

/**
 * 인증된 사용자. 컨트롤러는 {@link LoginUser} 로 이걸 받는다.
 *
 * <p>요청 본문의 authorId/ownerId 를 믿지 않기 위한 장치다.
 */
public record AuthPrincipal(UUID userId, String email, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public boolean isNot(UUID otherUserId) {
        return !userId.equals(otherUserId);
    }
}
