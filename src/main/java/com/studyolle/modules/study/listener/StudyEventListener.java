package com.studyolle.modules.study.listener;

import com.studyolle.infra.config.AppProperties;
import com.studyolle.infra.mail.EmailMessage;
import com.studyolle.infra.mail.EmailService;
import com.studyolle.modules.account.entity.Account;
import com.studyolle.modules.account.repository.AccountRepository;
import com.studyolle.modules.board.entity.Board;
import com.studyolle.modules.board.event.BoardCreatedEvent;
import com.studyolle.modules.notification.Notification;
import com.studyolle.modules.notification.NotificationRepository;
import com.studyolle.modules.notification.NotificationType;
import com.studyolle.modules.study.event.StudyCreatedEvent;
import com.studyolle.modules.study.event.StudyUpdateEvent;
import com.studyolle.modules.study.repository.StudyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * StudyEventListener - 스터디 도메인 이벤트를 비동기로 처리하는 리스너
 *
 * =============================================
 * 역할과 책임
 * =============================================
 *
 * StudyService/StudySettingsService에서 발행한 도메인 이벤트를 수신하여
 * 알림(Notification)과 이메일을 비동기적으로 처리합니다.
 *
 * 이벤트 기반 아키텍처의 이점:
 * - 서비스 계층은 핵심 비즈니스 로직에만 집중 (단일 책임 원칙)
 * - 알림/이메일 로직이 분리되어 유지보수 용이
 * - @Async를 통한 비동기 처리로 사용자 응답 시간에 영향 없음
 *
 * =============================================
 * 이벤트별 처리 전략
 * =============================================
 *
 * 1. StudyCreatedEvent (스터디 공개)
 *    - 대상: 관심사(태그/지역)가 일치하는 모든 사용자
 *    - 조회: AccountPredicates.findByTagsAndZones() (QueryDSL)
 *    - 목적: 새 스터디를 잠재적 멤버에게 알림
 *
 * 2. StudyUpdateEvent (스터디 수정)
 *    - 대상: 해당 스터디의 관리자 + 멤버 전원
 *    - 조회: study.getManagers() + study.getMembers()
 *    - 목적: 기존 참여자에게 변경 사항 알림
 *
 * =============================================
 * @Async + @Transactional 조합
 * =============================================
 *
 * @Async는 이 클래스의 메서드가 별도 스레드에서 실행되도록 합니다.
 * 이벤트를 발행한 서비스의 트랜잭션과는 독립적인 새 트랜잭션이 필요하므로,
 * @Transactional을 함께 선언하여 DB 작업(알림 저장)이 가능하도록 합니다.
 *
 * 주의: @Async 메서드는 프록시 기반으로 동작하므로,
 * 같은 클래스 내의 메서드를 호출하면 비동기가 적용되지 않습니다.
 *
 * =============================================
 * Repository 직접 접근에 대한 아키텍처 설명
 * =============================================
 *
 * 이 리스너는 StudyRepository, AccountRepository, NotificationRepository에 직접 접근합니다.
 * 일반적으로 Controller -> Service -> Repository 원칙을 따르지만,
 * 이벤트 리스너는 컨트롤러가 아닌 "인프라스트럭처 컴포넌트"이며,
 * 서비스 계층의 비즈니스 로직을 위임하는 것이 아니라
 * 알림/이메일이라는 독립적인 인프라 작업을 수행하므로
 * Repository 직접 접근이 정당화됩니다.
 */
@Slf4j
@Transactional
@Component
@Async
@RequiredArgsConstructor
public class StudyEventListener {

    private final StudyRepository studyRepository;
    private final AccountRepository accountRepository;
    private final EmailService emailService;
    private final TemplateEngine templateEngine;
    private final AppProperties appProperties;
    private final NotificationRepository notificationRepository;

    // ============================
    // 이벤트 핸들러
    // ============================

    /**
     * 스터디 공개(Created) 이벤트 처리.
     *
     * 처리 흐름:
     * 1. 이벤트에서 스터디 ID 추출 -> tags, zones 포함하여 재조회
     * 2. AccountPredicates로 관심사가 일치하는 사용자 조회
     * 3. 각 사용자의 알림 설정에 따라 이메일/웹 알림 발송
     *
     * 스터디를 재조회하는 이유:
     * 이벤트 객체에 담긴 Study는 원래 트랜잭션의 영속성 컨텍스트에 속하지만,
     * @Async로 별도 스레드에서 실행되므로 새 트랜잭션이 시작됩니다.
     * 따라서 이전 영속성 컨텍스트가 닫혀 있어 지연 로딩이 불가능하므로,
     * 필요한 연관관계를 포함하여 새로 조회합니다.
     */
    @EventListener
    public void handleStudyCreatedEvent(StudyCreatedEvent studyCreatedEvent) {
        Study study = studyRepository.findStudyWithTagsAndZonesById(studyCreatedEvent.getStudy().getId());

        Iterable<Account> accounts = accountRepository.findAll(
                AccountPredicates.findByTagsAndZones(study.getTags(), study.getZones()));

        accounts.forEach(account -> {
            if (account.isStudyCreatedByEmail()) {
                sendStudyCreatedEmail(study, account,
                        "새로운 스터디가 생겼습니다",
                        "스터디올래, '" + study.getTitle() + "' 스터디가 생겼습니다.");
            }

            if (account.isStudyCreatedByWeb()) {
                createNotification(study, account, study.getShortDescription(), NotificationType.STUDY_CREATED);
            }
        });
    }

    /**
     * 스터디 수정(Update) 이벤트 처리.
     *
     * 처리 흐름:
     * 1. 이벤트에서 스터디 ID 추출 -> managers, members 포함하여 재조회
     * 2. 관리자 + 멤버를 합쳐 알림 대상 Set 구성 (중복 제거)
     * 3. 각 사용자의 알림 설정에 따라 이메일/웹 알림 발송
     */
    @EventListener
    public void handleStudyUpdateEvent(StudyUpdateEvent studyUpdateEvent) {
        Study study = studyRepository.findStudyWithManagersAndMembersById(studyUpdateEvent.getStudy().getId());

        Set<Account> accounts = new HashSet<>();
        accounts.addAll(study.getManagers());
        accounts.addAll(study.getMembers());

        accounts.forEach(account -> {
            if (account.isStudyUpdatedByEmail()) {
                sendStudyCreatedEmail(study, account,
                        studyUpdateEvent.getMessage(),
                        "스터디올래, '" + study.getTitle() + "' 스터디에 새소식이 있습니다.");
            }

            if (account.isStudyUpdatedByWeb()) {
                createNotification(study, account, studyUpdateEvent.getMessage(), NotificationType.STUDY_UPDATED);
            }
        });
    }

    /**
     * 게시글 생성(BoardCreated) 이벤트 처리
     *
     * 처리 흐름:
     * 1. 이벤트에서 Board → Study ID 추출 → managers, members 포함하여 재조회
     * 2. 관리자 + 멤버를 합쳐 알림 대상 Set 구성 (작성자 본인 제외)
     * 3. 각 사용자의 알림 설정에 따라 이메일/웹 알림 발송
     *
     * @Async로 별도 스레드에서 실행되므로, 이벤트 발행 시점의 영속성 컨텍스트가 닫혀 있어 Study를 managers/members 포함하여 새로 조회합니다.
     */
    @EventListener
    public void handleBoardCreatedEvent(BoardCreatedEvent boardCreatedEvent) {
        Board board = boardCreatedEvent.getBoard();
        Study study = studyRepository.findStudyWithManagersAndMembersById(board.getStudy().getId());

        Set<Account> accounts = new HashSet<>();
        accounts.addAll(study.getManagers());
        accounts.addAll(study.getMembers());
        accounts.remove(board.getCreatedBy()); // 작성자 본인 제외

        accounts.forEach(account -> {
            if (account.isStudyUpdatedByEmail()) {
                sendStudyCreatedEmail(study, account,
                        "'" + board.getTitle() + "' 새 게시글이 등록되었습니다.",
                        "스터디올래, '" + study.getTitle() + "' 스터디에 새 게시글이 있습니다.");
            }

            if (account.isStudyUpdatedByWeb()) {
                Notification notification = new Notification();
                notification.setTitle(study.getTitle());
                notification.setLink("/study/" + study.getEncodedPath() + "/board/" + board.getId());
                notification.setChecked(false);
                notification.setCreatedDateTime(LocalDateTime.now());
                notification.setMessage("'" + board.getTitle() + "' 새 게시글이 등록되었습니다.");
                notification.setAccount(account);
                notification.setNotificationType(NotificationType.STUDY_NEW_BOARD);
                notificationRepository.save(notification);
            }
        });
    }

    // ============================
    // 알림 생성 헬퍼
    // ============================

    /**
     * 웹 알림(Notification)을 생성하여 DB에 저장합니다.
     * 사용자는 웹 UI의 알림 목록에서 이 알림을 확인할 수 있습니다.
     *
     * @param study            알림과 연관된 스터디
     * @param account          알림 수신자
     * @param message          알림 본문 메시지
     * @param notificationType 알림 유형 (STUDY_CREATED / STUDY_UPDATED)
     */
    private void createNotification(Study study, Account account, String message, NotificationType notificationType) {
        Notification notification = new Notification();
        notification.setTitle(study.getTitle());
        notification.setLink("/study/" + study.getEncodedPath());
        notification.setChecked(false);
        notification.setCreatedDateTime(LocalDateTime.now());
        notification.setMessage(message);
        notification.setAccount(account);
        notification.setNotificationType(notificationType);
        notificationRepository.save(notification);
    }

    // ============================
    // 이메일 발송 헬퍼
    // ============================

    /**
     * 스터디 관련 이메일을 발송합니다.
     *
     * Thymeleaf 템플릿("mail/simple-link")을 사용하여 HTML 이메일 본문을 생성합니다.
     * 템플릿에 전달되는 변수:
     * - nickname: 수신자 이름 (개인화된 인사말)
     * - link: 스터디 상세 페이지 경로
     * - linkName: 링크 텍스트 (스터디 제목)
     * - message: 본문 안내 메시지
     * - host: 서비스 기본 URL (e.g., https://studyolle.com)
     *
     * @param study          알림 대상 스터디
     * @param account        이메일 수신자
     * @param contextMessage 이메일 본문에 포함될 안내 메시지
     * @param emailSubject   이메일 제목
     */
    private void sendStudyCreatedEmail(Study study, Account account, String contextMessage, String emailSubject) {
        Context context = new Context();
        context.setVariable("nickname", account.getNickname());
        context.setVariable("link", "/study/" + study.getEncodedPath());
        context.setVariable("linkName", study.getTitle());
        context.setVariable("message", contextMessage);
        context.setVariable("host", appProperties.getHost());

        String message = templateEngine.process("mail/simple-link", context);

        EmailMessage emailMessage = EmailMessage.builder()
                .subject(emailSubject)
                .to(account.getEmail())
                .message(message)
                .build();

        emailService.sendEmail(emailMessage);
    }
}