package com.otboo.config;

import com.otboo.auth.password.LoggingPasswordResetMailer;
import com.otboo.auth.password.PasswordResetMailer;
import com.otboo.auth.password.PasswordResetProperties;
import com.otboo.auth.password.SmtpPasswordResetMailer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * 메일 발송 수단을 고른다.
 *
 * <p>스프링 부트는 {@code spring.mail.host} 가 있을 때만 {@code JavaMailSender} 를 만든다.
 * 그래서 "그 빈이 있는가"로 판단하면 설정과 어긋날 일이 없다 — 프로퍼티를 따로 읽어
 * 비교하면 한쪽만 고쳤을 때 조용히 갈라진다.
 *
 * <p>SMTP 설정은 {@code SPRING_MAIL_HOST} 등 환경변수로 넣는다. application.yml 에
 * {@code host} 를 빈 값으로라도 적어두면 부트가 "설정됐다"고 보고 빈 주소로 발송을 시도한다.
 */
@Configuration
@EnableConfigurationProperties(PasswordResetProperties.class)
public class PasswordResetMailerConfig {

    @Bean
    PasswordResetMailer passwordResetMailer(ObjectProvider<JavaMailSender> mailSender,
                                            PasswordResetProperties properties,
                                            Environment environment) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender != null) {
            return new SmtpPasswordResetMailer(sender, properties.from());
        }
        // 설정을 빠뜨린 채 배포하면 임시 비밀번호가 메일 대신 로그로 나간다.
        // 조용히 넘어가느니 기동을 실패시킨다.
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException(
                    "prod 에서는 SPRING_MAIL_HOST 설정이 필요합니다. "
                            + "임시 비밀번호를 메일 대신 로그로 내보낼 수 없습니다.");
        }
        return new LoggingPasswordResetMailer();
    }
}
