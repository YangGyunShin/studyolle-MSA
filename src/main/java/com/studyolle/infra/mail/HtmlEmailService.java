package com.studyolle.infra.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

// "dev" 프로파일일 때만 이 빈이 스프링 컨테이너에 등록됨
@Profile({"dev", "local"})
@Component
// final 필드에 대한 생성자를 자동 생성 → JavaMailSender를 생성자 주입
@RequiredArgsConstructor
@Slf4j
// EmailService 인터페이스를 구현 → ConsoleEmailService와 동일한 인터페이스를 공유
public class HtmlEmailService implements EmailService {

    // 스프링이 제공하는 메일 발송 추상화 객체 (실제 SMTP 서버와 통신)
    private final JavaMailSender javaMailSender;

    @Override
    public void sendEmail(EmailMessage emailMessage) {
        // MIME 타입의 이메일 메시지 객체 생성 (HTML 본문을 담을 수 있음)
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        try {
            // MimeMessageHelper는 MimeMessage의 내용을 대신 설정해주는 헬퍼 객체
            // 생성자에서 mimeMessage를 넘겨주었으므로, helper의 setTo/setSubject/setText 호출이
            // 모두 해당 mimeMessage 객체에 반영됨
            // 두 번째 파라미터 false = 멀티파트(첨부파일) 사용하지 않음
            // 세 번째 파라미터 "UTF-8" = 한글 깨짐 방지를 위한 인코딩 설정
            MimeMessageHelper mimeMessageHelper = new MimeMessageHelper(mimeMessage, false, "UTF-8");
            mimeMessageHelper.setTo(emailMessage.getTo());           // 수신자 설정
            mimeMessageHelper.setSubject(emailMessage.getSubject()); // 제목 설정
            mimeMessageHelper.setText(emailMessage.getMessage(), true); // 본문 설정 (true = HTML로 처리)
            javaMailSender.send(mimeMessage); // 실제 이메일 발송 (SMTP 서버 통신)
            log.info("send email: {}", emailMessage.getMessage());
        } catch (MessagingException e) {
            // 이메일 발송 실패 시 에러 로그 출력 후 런타임 예외로 전환
            log.error("failed to send email", e);
            throw new RuntimeException(e);
        }
    }
}