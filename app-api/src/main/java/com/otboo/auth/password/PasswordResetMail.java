package com.otboo.auth.password;

import com.otboo.user.entity.OAuthProvider;
import java.util.List;

/**
 * 보낼 메일 한 통.
 *
 * <p>문구를 여기 모아두는 이유 — 발송 수단이 로그와 SMTP 두 가지다. 구현체마다 문구를 쓰면
 * 로컬에서 로그로 확인한 내용과 실제로 사용자에게 가는 메일이 조용히 달라진다.
 * 수단은 갈라져도 내용은 하나여야 한다.
 */
public record PasswordResetMail(String to, String subject, String body) {

    public static PasswordResetMail temporaryPassword(String to, String temporaryPassword) {
        return new PasswordResetMail(to, "[옷장을 부탁해] 임시 비밀번호 안내", """
                요청하신 임시 비밀번호입니다.

                    %s

                기존 비밀번호는 더 이상 사용할 수 없습니다.
                로그인한 뒤 마이페이지에서 새 비밀번호로 바꿔주세요.

                본인이 요청한 것이 아니라면 지금 바로 비밀번호를 변경해주세요.
                """.formatted(temporaryPassword));
    }

    /**
     * 소셜 전용 계정에 보내는 안내.
     *
     * <p>임시 비밀번호를 만들어 보내면 "소셜 계정에는 비밀번호를 두지 않는다"는 결정이 깨진다.
     * 비밀번호를 만들지 않고 어디로 로그인해야 하는지만 알려준다.
     */
    public static PasswordResetMail socialLoginNotice(String to, List<OAuthProvider> providers) {
        String names = providers.isEmpty()
                ? "소셜 로그인"
                : providers.stream().map(PasswordResetMail::displayName).distinct()
                        .reduce((a, b) -> a + ", " + b).orElseThrow();
        return new PasswordResetMail(to, "[옷장을 부탁해] 소셜 로그인 계정입니다", """
                비밀번호 초기화를 요청하셨지만 이 계정은 소셜 로그인 전용이라 비밀번호가 없습니다.
                아래 방법으로 로그인해주세요.

                    %s

                본인이 요청한 것이 아니라면 이 메일을 무시하셔도 됩니다. 계정은 그대로입니다.
                """.formatted(names));
    }

    /**
     * 로그에 남길 수신 주소. 전체 주소를 그대로 찍으면 로그가 곧 회원 명단이 된다
     * ({@code com.otboo.common.logging} 규약).
     */
    public String maskedTo() {
        int at = to.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return to.charAt(0) + "***" + to.substring(at);
    }

    private static String displayName(OAuthProvider provider) {
        return switch (provider) {
            case GOOGLE -> "구글";
            case KAKAO -> "카카오";
        };
    }
}
