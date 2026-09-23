package com.otboo.auth.password;

import com.otboo.common.logging.LogKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * 실제 SMTP 발송. {@code spring.mail.host} 가 설정됐을 때만 선택된다.
 *
 * <p>예외를 잡지 않는다. 발송 실패를 여기서 삼키면 비밀번호는 바뀌었는데 사용자는
 * 새 비밀번호를 모르는 상태로 계정에서 잠긴다. 호출한 쪽이 되돌릴 수 있게 그대로 올린다.
 */
@Slf4j
@RequiredArgsConstructor
public class SmtpPasswordResetMailer implements PasswordResetMailer {

    private final JavaMailSender mailSender;

    /** 보내는 사람 주소. Gmail 은 인증 계정과 다른 주소로 보내면 거절한다. */
    private final String from;

    @Override
    public void send(PasswordResetMail mail) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(mail.to());
        message.setSubject(mail.subject());
        message.setText(mail.body());

        mailSender.send(message);

        // 본문은 찍지 않는다. 임시 비밀번호가 들어 있다.
        log.info("{} to={} subject={} sent=true",
                LogKeys.EVENT_MAIL, mail.maskedTo(), mail.subject());
    }
}
