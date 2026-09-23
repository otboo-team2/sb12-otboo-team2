package com.otboo.auth.password;

/**
 * 메일 발송. 구현은 SMTP 설정 유무로 갈리며 고르는 곳은 {@code PasswordResetMailerConfig} 다.
 *
 * <p>발송 실패는 예외로 알린다. 삼키면 비밀번호만 바뀌고 사용자는 그 사실을 모르는
 * 최악의 상태가 된다 — {@link PasswordResetService} 가 예외를 받아 되돌린다.
 */
public interface PasswordResetMailer {

    void send(PasswordResetMail mail);
}
