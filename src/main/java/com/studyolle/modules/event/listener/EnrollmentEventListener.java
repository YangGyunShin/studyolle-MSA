package com.studyolle.modules.event.listener;

import com.studyolle.infra.config.AppProperties;
import com.studyolle.infra.mail.EmailMessage;
import com.studyolle.infra.mail.EmailService;
import com.studyolle.modules.account.entity.Account;
import com.studyolle.modules.event.entity.Enrollment;
import com.studyolle.modules.event.entity.Event;
import com.studyolle.modules.event.event.EnrollmentEvent;
import com.studyolle.modules.notification.Notification;
import com.studyolle.modules.notification.NotificationRepository;
import com.studyolle.modules.notification.NotificationType;
import com.studyolle.modules.study.entity.Study;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.LocalDateTime;

/**
 * 참가 신청 상태 변경 알림을 수신하여 처리하는 이벤트 리스너
 *
 * [이 클래스의 역할]
 *
 * EventService에서 발행한 EnrollmentEvent(승인/거절 알림 메시지)를 수신하여,
 * 사용자에게 이메일과 웹 알림을 발송하는 "알림 후처리 담당자"이다.
 *
 * [전체 이벤트 시스템에서의 위치]
 *
 *   EventService (발행자, Producer)
 *     │
 *     │  eventPublisher.publishEvent(new EnrollmentAcceptedEvent(enrollment))
 *     │  eventPublisher.publishEvent(new EnrollmentRejectedEvent(enrollment))
 *     ▼
 *   Spring ApplicationEvent 시스템 (메시지 전달)
 *     │
 *     │  @EventListener가 붙은 메서드를 자동으로 찾아서 호출
 *     ▼
 *   EnrollmentEventListener (수신자, Consumer) ← 현재 클래스
 *     ├── 이메일 발송 (sendEmail)
 *     └── 웹 알림 저장 (createNotification)
 *
 * [왜 서비스에서 직접 알림을 보내지 않고 이벤트로 분리하는가?]
 *
 * 분리하지 않으면 EventService.acceptEnrollment() 안에
 * 이메일 발송, 웹 알림 저장, 향후 Slack/카카오톡 알림 등의 코드가 모두 들어간다.
 *
 *   문제점:
 *   1. 알림 채널이 추가될 때마다 서비스 코드가 비대해진다
 *   2. 이메일 서버 장애가 승인 로직의 트랜잭션까지 영향을 미친다
 *   3. 승인 로직과 알림 로직의 변경 사유가 다른데 같은 클래스에 있게 된다 (SRP 위반)
 *
 *   이벤트 분리 시:
 *   1. 서비스는 "상태 변경 + 이벤트 발행"만 하고 알림은 모른다
 *   2. 알림 채널 추가 시 리스너만 수정하면 된다 (개방-폐쇄 원칙)
 *   3. @Async로 비동기 처리하여 알림이 느려도 사용자 응답은 빠르다
 *   4. 알림 시스템 장애가 도메인 로직에 전파되지 않는다
 *
 * [클래스 레벨 어노테이션 설명]
 *
 * @Async
 *   - 이 클래스의 모든 public 메서드가 별도 스레드에서 비동기 실행된다.
 *   - 메인 스레드(사용자 요청 처리)는 이벤트를 발행한 즉시 응답을 반환하고,
 *     이메일 발송이나 알림 저장은 백그라운드에서 처리된다.
 *   - @Async가 동작하려면 @EnableAsync 설정이 필요하다. (보통 설정 클래스에 선언됨)
 *
 * @Transactional
 *   - 이 리스너 내부에서 notificationRepository.save()로 DB 쓰기가 발생하므로,
 *     별도의 트랜잭션이 필요하다.
 *   - @Async와 함께 사용되면 메인 트랜잭션과는 완전히 독립된 새 트랜잭션이 생성된다.
 *
 * @Component
 *   - 스프링 빈으로 등록하여 @EventListener가 동작하도록 한다.
 *   - 스프링은 빈으로 등록된 클래스 중 @EventListener가 붙은 메서드를 자동 스캔한다.
 */
@Slf4j
@Async
@Component
@Transactional
@RequiredArgsConstructor
public class EnrollmentEventListener {

    private final NotificationRepository notificationRepository;  // 웹 알림 DB 저장
    private final AppProperties appProperties;                    // 시스템 설정 (호스트 URL 등)
    private final TemplateEngine templateEngine;                  // Thymeleaf 이메일 템플릿 렌더링
    private final EmailService emailService;                      // 이메일 SMTP 발송

    /**
     * EnrollmentEvent를 수신하여 알림을 발송하는 핸들러 메서드
     *
     * [수신 범위]
     * 파라미터 타입이 EnrollmentEvent(추상 부모)이므로,
     * 이 타입의 모든 하위 클래스 이벤트를 수신한다:
     *   - EnrollmentAcceptedEvent (승인) → 이 메서드가 수신
     *   - EnrollmentRejectedEvent (거절) → 이 메서드가 수신
     *   - 향후 추가되는 EnrollmentXxxEvent → 이 메서드가 자동 수신
     *
     * 하위 클래스마다 별도 핸들러를 만들 필요 없이, 부모 타입 하나로 모두 처리 가능하다.
     * 이것이 EnrollmentEvent를 추상 클래스로 설계한 핵심 이유이다.
     *
     * [처리 흐름]
     *   1. 이벤트 메시지에서 enrollment -> account, event, study 정보 추출
     *   2. 해당 사용자의 알림 수신 설정 확인
     *   3. 이메일 알림 허용 시 → sendEmail() 호출
     *   4. 웹 알림 허용 시 → createNotification() 호출
     *
     * [사용자 알림 설정이란?]
     * Account 엔티티에 알림 수신 여부 설정 필드가 있다:
     *   - isStudyEnrollmentResultByEmail(): 이메일로 참가 결과 알림 받을지
     *   - isStudyEnrollmentResultByWeb(): 웹 알림함으로 참가 결과 알림 받을지
     * 사용자가 프로필 설정에서 직접 on/off 할 수 있다.
     */
    @EventListener
    public void handleEnrollmentEvent(EnrollmentEvent enrollmentEvent) {

        // 이벤트 메시지에서 필요한 도메인 객체들을 추출
        Enrollment enrollment = enrollmentEvent.getEnrollment();
        Account account = enrollment.getAccount();   // 알림 수신 대상자
        Event event = enrollment.getEvent();          // 어떤 모임인지
        Study study = event.getStudy();               // 어떤 스터디인지

        // 사용자의 이메일 알림 설정이 켜져있으면 이메일 발송
        if (account.isStudyEnrollmentResultByEmail()) {
            sendEmail(enrollmentEvent, account, event, study);
        }

        // 사용자의 웹 알림 설정이 켜져있으면 웹 알림 저장
        if (account.isStudyEnrollmentResultByWeb()) {
            createNotification(enrollmentEvent, account, event, study);
        }
    }

    /**
     * 참가 신청 결과 이메일 발송
     *
     * [이메일 생성 과정]
     *   1. Thymeleaf Context에 템플릿 변수들을 바인딩
     *      - nickname: 수신자 이름 (이메일 본문에서 "OO님" 표시)
     *      - link: 이메일에서 클릭할 URL (해당 모임 상세 페이지)
     *      - linkName: 링크에 표시할 텍스트 (스터디 제목)
     *      - message: 승인/거절 안내 메시지 (EnrollmentEvent에서 가져옴)
     *      - host: 서비스 도메인 URL (application.yml의 app.host 설정값)
     *
     *   2. Thymeleaf TemplateEngine으로 HTML 본문 생성
     *      - resources/templates/mail/simple-link.html 템플릿 사용
     *      - 위에서 바인딩한 변수들이 템플릿의 th:text, th:href 등에 삽입됨
     *
     *   3. EmailMessage 객체 생성 후 EmailService로 발송
     *      - EmailService 내부에서 실제 SMTP 서버와 통신하여 이메일 전송
     */
    private void sendEmail(EnrollmentEvent enrollmentEvent,
                           Account account,
                           Event event,
                           Study study) {

        // Thymeleaf 템플릿에 전달할 변수 바인딩
        Context context = new Context();
        context.setVariable("nickname", account.getNickname());
        context.setVariable("link", "/study/" + study.getEncodedPath() + "/events/" + event.getId());
        context.setVariable("linkName", study.getTitle());
        context.setVariable("message", enrollmentEvent.getMessage());
        context.setVariable("host", appProperties.getHost());

        // 템플릿 렌더링 → HTML 문자열 생성
        String message = templateEngine.process("mail/simple-link", context);

        // 이메일 메시지 객체 생성 및 발송
        EmailMessage emailMessage = EmailMessage.builder()
                .subject("스터디올래, " + event.getTitle() + " 모임 참가 신청 결과입니다.")
                .to(account.getEmail())
                .message(message)
                .build();

        emailService.sendEmail(emailMessage);
    }

    /**
     * 웹 알림(Notification) 생성 및 DB 저장
     *
     * 사용자가 웹 페이지 상단의 알림 아이콘을 클릭하면 보이는 알림 목록에 추가된다.
     *
     * [Notification 엔티티 필드 설명]
     *   - title: 알림 제목 → "스터디명 / 모임명" 형식
     *   - link: 알림 클릭 시 이동할 URL → 해당 모임 상세 페이지
     *   - checked: 읽음 여부 → 새 알림은 false (안 읽음)
     *   - createdDateTime: 알림 생성 시각
     *   - message: 상세 내용 → EnrollmentEvent의 승인/거절 메시지
     *   - account: 알림 수신자
     *   - notificationType: 알림 분류 → EVENT_ENROLLMENT (참가 신청 결과)
     */
    private void createNotification(EnrollmentEvent enrollmentEvent,
                                    Account account,
                                    Event event,
                                    Study study) {

        Notification notification = new Notification();
        notification.setTitle(study.getTitle() + " / " + event.getTitle());
        notification.setLink("/study/" + study.getEncodedPath() + "/events/" + event.getId());
        notification.setChecked(false);
        notification.setCreatedDateTime(LocalDateTime.now());
        notification.setMessage(enrollmentEvent.getMessage());
        notification.setAccount(account);
        notification.setNotificationType(NotificationType.EVENT_ENROLLMENT);

        notificationRepository.save(notification);
    }
}