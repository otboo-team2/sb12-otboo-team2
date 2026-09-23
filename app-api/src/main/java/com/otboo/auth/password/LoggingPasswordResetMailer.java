package com.otboo.auth.password;

import com.otboo.common.logging.LogKeys;
import lombok.extern.slf4j.Slf4j;

/**
 * SMTP 가 설정되지 않았을 때 쓰는 구현. 메일 대신 내용을 로그로 찍는다.
 *
 * <p>덕분에 SMTP 계정 없이도 초기화 흐름 전체를 로컬에서 돌려볼 수 있다.
 *
 * <p>⚠️ <b>임시 비밀번호가 그대로 로그에 남는다.</b> 로컬 전용이며 {@code prod} 프로파일에서는
 * 이 구현이 선택되지 않고 기동이 실패한다({@code PasswordResetMailerConfig}).
 * 설정을 빠뜨린 채 배포했을 때 조용히 로그로 흘리는 것보다 안 뜨는 편이 낫다.
 */
@Slf4j
public class LoggingPasswordResetMailer implements PasswordResetMailer {

    @Override
    public void send(PasswordResetMail mail) {
        log.info("{} to={} subject={} sent=false (SMTP 미설정 — 실제로 발송하지 않았다)\n{}",
                LogKeys.EVENT_MAIL, mail.maskedTo(), mail.subject(), mail.body());
    }
}
