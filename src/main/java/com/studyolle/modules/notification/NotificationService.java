package com.studyolle.modules.notification;

import com.studyolle.modules.account.entity.Account;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 알림(Notification) 도메인 서비스
 *
 * - 알림 조회, 분류, 읽음 처리, 삭제 등 모든 알림 비즈니스 로직을 담당
 * - Controller는 이 서비스를 통해서만 알림 데이터에 접근함
 *   (Controller -> Repository 직접 접근 금지)
 *
 * =====================================================
 * [리팩토링 사유]
 *
 * 기존 구조에서는 NotificationController가 NotificationRepository를 직접 주입받아
 * 조회, 카운트, 삭제를 직접 수행했음. 이는 다음 문제를 유발:
 *
 *   (1) 계층 위반: Controller가 Repository에 직접 접근하면
 *       비즈니스 로직이 Controller에 흩어지고, Service 계층의 의미가 퇴색됨
 *
 *   (2) 트랜잭션 경계 모호: Controller에서 repository 메서드를 여러 번 호출하면
 *       각 호출이 별도 트랜잭션에서 실행되어 데이터 일관성 보장이 어려움
 *       예: 알림 조회 -> 읽음 처리가 서로 다른 트랜잭션에서 실행
 *
 *   (3) 테스트 어려움: Controller 테스트 시 Repository까지 모킹해야 하는 부담
 *
 * 리팩토링 후:
 *   Controller -> Service(비즈니스 로직 + 트랜잭션) -> Repository(데이터 접근)
 *   깔끔한 계층 분리 달성
 */
@Service
@Transactional  // 이 클래스의 모든 public 메서드에 쓰기 가능 트랜잭션 적용
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    /**
     * 읽지 않은 새 알림 목록 조회 (최신순)
     *
     * @param account 알림을 조회할 사용자
     * @return 읽지 않은(checked=false) 알림 리스트 (최신순 정렬)
     */
    @Transactional(readOnly = true)
    public List<Notification> getNewNotifications(Account account) {
        return notificationRepository
                .findByAccountAndCheckedOrderByCreatedDateTimeDesc(account, false);
    }

    /**
     * 읽은 과거 알림 목록 조회 (최신순)
     *
     * @param account 알림을 조회할 사용자
     * @return 읽은(checked=true) 알림 리스트 (최신순 정렬)
     */
    @Transactional(readOnly = true)
    public List<Notification> getOldNotifications(Account account) {
        return notificationRepository
                .findByAccountAndCheckedOrderByCreatedDateTimeDesc(account, true);
    }

    /**
     * 특정 사용자의 읽음/안읽음 알림 개수 조회
     *
     * @param account 알림 소유 사용자
     * @param checked true: 읽은 알림 카운트, false: 안 읽은 알림 카운트
     * @return 해당 조건의 알림 개수
     */
    @Transactional(readOnly = true)
    public long countByChecked(Account account, boolean checked) {
        return notificationRepository.countByAccountAndChecked(account, checked);
    }

    /**
     * 알림들을 일괄 읽음 처리
     *
     * - 전달된 알림 엔티티들의 checked 상태를 true로 변경
     * - @Transactional 범위 안에서 실행되므로 dirty checking에 의해 트랜잭션 커밋 시 자동으로 UPDATE 쿼리가 발생함
     * - 그럼에도 saveAll()을 명시적으로 호출하는 이유:
     *   호출자가 별도 트랜잭션에서 엔티티를 조회한 경우 detached 상태일 수 있음
     *   saveAll()은 merge를 통해 이런 경우에도 안전하게 저장함
     */
    public void markAsRead(List<Notification> notifications) {
        notifications.forEach(n -> n.setChecked(true));
        notificationRepository.saveAll(notifications);
    }

    /**
     * 읽은 알림 전체 삭제
     *
     * - 사용자의 checked=true 알림을 일괄 삭제
     *
     * @param account 알림 소유 사용자
     */
    public void deleteOldNotifications(Account account) {
        notificationRepository.deleteByAccountAndChecked(account, true);
    }

    /**
     * 알림을 유형(NotificationType)별로 분류
     *
     * - 하나의 알림 리스트를 3가지 유형별 리스트로 분리하여 반환
     * - Controller에서 Model에 담아 웹 UI의 탭별 표시에 사용
     *
     * =====================================================
     * [왜 서비스에서 분류하는가?]
     *
     * 이 분류 로직은 단순 반복문이지만, "어떤 기준으로 나누는가"는
     * 도메인 규칙(NotificationType 기반 분류)에 해당함.
     * Controller에 두면 표현 계층에 도메인 로직이 섞이므로 서비스로 이동.
     *
     * @param notifications 분류할 전체 알림 리스트
     * @return 유형별로 분류된 알림 리스트 배열
     *         [0] = STUDY_CREATED (새 스터디)
     *         [1] = EVENT_ENROLLMENT (모임 참가)
     *         [2] = STUDY_UPDATED (참여 중 스터디 업데이트)
     */
    @Transactional(readOnly = true)
    public List<List<Notification>> categorize(List<Notification> notifications) {
        List<Notification> newStudyNotifications = new ArrayList<>();
        List<Notification> eventEnrollmentNotifications = new ArrayList<>();
        List<Notification> watchingStudyNotifications = new ArrayList<>();
        List<Notification> newBoardNotifications = new ArrayList<>();

        for (var notification : notifications) {
            switch (notification.getNotificationType()) {
                case STUDY_CREATED:
                    newStudyNotifications.add(notification);
                    break;
                case EVENT_ENROLLMENT:
                    eventEnrollmentNotifications.add(notification);
                    break;
                case STUDY_UPDATED:
                    watchingStudyNotifications.add(notification);
                    break;
                case STUDY_NEW_BOARD:
                    newBoardNotifications.add(notification);
                    break;
            }
        }

        return List.of(newStudyNotifications, eventEnrollmentNotifications,
                watchingStudyNotifications, newBoardNotifications);
    }

    /**
     * 개별 알림 읽음 처리 후 반환
     * - 본인의 알림만 읽음 처리 가능하도록 account 검증
     */
    public Notification markAsReadAndGet(Account account, Long id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("알림을 찾을 수 없습니다. id=" + id));

        // 본인의 알림인지 확인
        if (!notification.getAccount().equals(account)) {
            throw new AccessDeniedException("권한이 없습니다.");
        }

        notification.setChecked(true);
        return notificationRepository.save(notification);
    }
}