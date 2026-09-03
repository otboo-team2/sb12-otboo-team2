package com.otboo.auth.dto;

import com.otboo.user.dto.UserDto;

/**
 * 로그인·재발급 응답. 리프레시 토큰은 여기 담지 않고 HttpOnly 쿠키로만 내보낸다.
 * 본문에 담으면 XSS 로 탈취될 수 있다.
 */
public record JwtDto(UserDto userDto, String accessToken) {
}
