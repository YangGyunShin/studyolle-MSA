package com.studyolle.infra.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 개발/테스트 환경용 이메일 서비스 구현체
 * 실제 이메일을 발송하지 않고 콘솔(로그)에 이메일 내용만 출력합니다.
 * 
 * 프로덕션 환경에서는 HtmlEmailService가 실제 이메일을 발송합니다.
 */
@Slf4j  // Lombok: log 객체를 자동 생성 (log.info() 사용 가능)
@Profile({"test"})  // "local" 또는 "test" 프로파일에서만 이 빈이 활성화됨
@Component  // Spring 빈으로 등록
public class ConsoleEmailService implements EmailService {

    /**
     * 이메일 발송 메서드 (가짜 구현)
     * 실제로 이메일을 보내지 않고, 로그로 메시지 내용만 출력합니다.
     * 개발 중 이메일 발송 로직 테스트 시 유용합니다.
     * 
     * @param emailMessage 발송할 이메일 정보 (수신자, 제목, 내용 등)
     */
    @Override
    public void sendEmail(EmailMessage emailMessage) {
        log.info("send email: {}", emailMessage.getMessage());
    }
}