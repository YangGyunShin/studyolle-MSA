package com.studyolle.account.infra.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// "local" 프로파일에서 활성화: 실제 이메일을 보내지 않고 콘솔 로그로 출력
// HtmlEmailService는 "dev", "local" 프로파일에서 활성화
// 둘 다 local에서 active하면 빈 충돌이 발생하므로, ConsoleEmailService를 local 전용으로 한정
@Slf4j
@Profile("local")
@Component
public class ConsoleEmailService implements EmailService {

    @Override
    public void sendEmail(EmailMessage emailMessage) {
        log.info("===== [Console Email Service] =====");
        log.info("To      : {}", emailMessage.getTo());
        log.info("Subject : {}", emailMessage.getSubject());
        log.info("Message : {}", emailMessage.getMessage());
        log.info("===================================");
    }
}