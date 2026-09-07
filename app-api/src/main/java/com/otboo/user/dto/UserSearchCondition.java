package com.otboo.user.dto;

import com.otboo.user.entity.Role;

/**
 * 계정 목록 필터. 관리자 화면({@code UserListFilter.tsx})이 보내는 세 가지다.
 *
 * @param emailLike 이메일 부분 일치. 없으면 전체
 * @param roleEqual 권한 완전 일치
 * @param locked    잠금 여부. {@code null} 이면 잠금·해제 모두
 */
public record UserSearchCondition(
        String emailLike,
        Role roleEqual,
        Boolean locked
) {

    public UserSearchCondition {
        if (emailLike != null) {
            emailLike = emailLike.trim();
            if (emailLike.isEmpty()) {
                emailLike = null;
            }
        }
    }
}
