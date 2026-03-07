package com.studyolle.modules.event.service;

import com.studyolle.modules.account.entity.Account;
import com.studyolle.modules.event.entity.Enrollment;
import com.studyolle.modules.event.entity.Event;
import com.studyolle.modules.event.event.EnrollmentAcceptedEvent;
import com.studyolle.modules.event.event.EnrollmentRejectedEvent;
import com.studyolle.modules.event.repository.EnrollmentRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 참가 신청(Enrollment) 관련 비즈니스 로직을 담당하는 서비스
 *
 * [이 클래스의 역할]
 *
 * Enrollment 엔티티의 전체 생명주기를 관리한다:
 *   - 생성: 사용자가 모임에 참가 신청
 *   - 삭제: 사용자가 참가 신청 취소
 *   - 상태 변경: 운영자가 승인/거절/출석체크/출석취소
 *
 * EnrollmentController(일반 사용자)와 EnrollmentManageController(운영자) 두 Controller가
 * 이 하나의 Service를 공유한다.
 *
 *
 * [두 Controller가 하나의 Service를 공유하는 이유]
 *
 * Controller를 사용자 역할(일반 vs 운영자) 기준으로 분리한 것과 달리,
 * Service는 대상 엔티티(Enrollment) 기준으로 묶는 것이 더 적합하다.
 *
 *   이유 1: 메서드 간 도메인 의존성
 *     cancelEnrollment()에서 참가 취소 후 acceptNextWaitingEnrollment()를 호출한다.
 *     "취소"는 일반 사용자 기능이고 "대기자 자동 승격"은 관리 기능인데,
 *     하나의 트랜잭션 안에서 연속으로 실행되어야 한다.
 *     이들이 서로 다른 Service에 있으면 불필요한 의존성이 생긴다.
 *
 *   이유 2: 동일 Repository 사용
 *     모든 메서드가 EnrollmentRepository를 사용한다.
 *     같은 Repository를 다루는 로직은 하나의 Service에 있는 것이 자연스럽다.
 *
 *   이유 3: 변경 사유의 동질성
 *     "참가 정책이 바뀌면" 신청/취소/승인/거절 모두 함께 변경될 가능성이 높다.
 *     예: "출석 체크를 해야만 승인이 유효하다"는 정책이 추가되면
 *         checkIn과 accept 로직이 동시에 수정되어야 한다.
 *
 *
 * [전체 서비스 구조에서의 위치]
 *
 *   EventController ──────────→ EventService
 *     (모임 CRUD)                  - createEvent()
 *                                  - updateEvent()
 *                                  - deleteEvent()
 *
 *   EnrollmentController ─────→ EnrollmentService (현재 클래스)
 *     (일반 사용자)                - newEnrollment()
 *         │                       - cancelEnrollment()
 *         │
 *   EnrollmentManageController → EnrollmentService (현재 클래스)
 *     (운영자)                    - acceptEnrollment()
 *                                 - rejectEnrollment()
 *                                 - checkInEnrollment()
 *                                 - cancelCheckInEnrollment()
 *
 *
 * [참가 신청의 상태 전이 다이어그램]
 *
 * 하나의 Enrollment가 거칠 수 있는 상태 변화:
 *
 *   ┌───────────────────────────────────────────────────────┐
 *   │                                                       │
 *   │   [신청] ──→ [대기중] ──→ [승인됨] ──→ [출석완료]            │
 *   │     │     accepted=false  accepted=true  attended=true│
 *   │     │          │              │                       │
 *   │     │          │              ├──→ [거절됨]             │
 *   │     │          │              │   accepted=false      │
 *   │     │          │              │                       │
 *   │     │          │              └──→ [출석취소]           │
 *   │     │          │                  attended=false      │
 *   │     │          │                                      │
 *   │     │          └──→ [취소/삭제] (DB에서 제거)             │
 *   │     │                                                 │
 *   │     └──→ [즉시 승인] (FCFS이고 정원 남은 경우)               │
 *   │         accepted=true                                 │
 *   │                                                       │
 *   └───────────────────────────────────────────────────────┘
 *
 *   - FCFS 방식: 신청 → 즉시 승인 or 대기중 → (취소 시 대기자 자동 승격)
 *   - CONFIRMATIVE 방식: 신청 → 대기중 → 운영자가 승인/거절
 */
@Service
@Transactional
@RequiredArgsConstructor
public class EnrollmentService {

    /** Enrollment 엔티티 저장소 */
    private final EnrollmentRepository enrollmentRepository;

    /**
     * Spring ApplicationEvent 발행기
     *
     * 참가 신청 승인/거절 시 알림 이벤트를 발행한다.
     *
     *   이 서비스에서 발행하는 이벤트:
     *     EnrollmentAcceptedEvent → 승인 알림 (이메일 + 웹 알림)
     *     EnrollmentRejectedEvent → 거절 알림 (이메일 + 웹 알림)
     *
     *   수신자: EnrollmentEventListener.handleEnrollmentEvent()
     */
    private final ApplicationEventPublisher eventPublisher;

    // ====================================================================
    // 일반 사용자 기능 (EnrollmentController에서 호출)
    // ====================================================================

    /**
     * 새로운 참가 신청을 생성한다
     *
     * [처리 흐름]
     *
     *   사용자가 "참가 신청" 버튼 클릭
     *     → EnrollmentController.enroll()
     *     → 이 메서드 호출
     *     → 중복 신청 확인 → Enrollment 생성 → 자동 승인 여부 결정 → DB 저장
     *
     * [자동 승인 판단 로직]
     *
     * 신청 시점에 event.isAbleToAcceptWaitingEnrollment()를 호출하여
     * 즉시 승인할지 대기 상태로 둘지 결정한다.
     *
     *   FCFS(선착순) + 정원 남음 → accepted = true (즉시 승인)
     *   FCFS(선착순) + 정원 꽉참 → accepted = false (대기)
     *   CONFIRMATIVE(수동 승인)  → accepted = false (항상 대기)
     *
     * [중복 신청 방지]
     *
     * existsByEventAndAccount()로 먼저 확인한다.
     * 같은 사용자가 같은 모임에 두 번 신청하는 것을 방지한다.
     * 대기 중이든 승인됐든 관계없이, 이미 신청이 존재하면 새로 생성하지 않는다.
     *
     * [양방향 연관관계 설정]
     *
     * event.addEnrollment(enrollment)를 호출하여 양방향 연관관계를 설정한다.
     *   - Event.enrollments 리스트에 추가 (비주인 측, 메모리)
     *   - Enrollment.event에 FK 설정 (주인 측, DB)
     *
     * 이후 enrollmentRepository.save()로 Enrollment를 영속화한다.
     * (새로 생성된 엔티티이므로 save() 호출이 필요함 - dirty checking 대상이 아님)
     *
     * @param event   참가 신청할 모임
     * @param account 참가 신청하는 사용자 (현재 로그인한 사용자)
     */
    public void newEnrollment(Event event, Account account) {

        // 중복 신청 확인 - 이미 신청한 경우 아무 작업도 하지 않음
        if (!enrollmentRepository.existsByEventAndAccount(event, account)) {

            // 새로운 참가 신청 엔티티 생성
            Enrollment enrollment = new Enrollment();
            enrollment.setEnrolledAt(LocalDateTime.now());  // 신청 시각 기록

            // 자동 승인 여부 결정
            // → FCFS이고 정원이 남아있으면 true, 그 외에는 false
            enrollment.setAccepted(event.isAbleToAcceptWaitingEnrollment());

            // 신청자 정보 설정
            enrollment.setAccount(account);

            // 양방향 연관관계 설정 (Event <-> Enrollment)
            event.addEnrollment(enrollment);

            // DB 저장 (새 엔티티이므로 INSERT 쿼리 실행)
            enrollmentRepository.save(enrollment);
        }
    }

    /**
     * 참가 신청을 취소(삭제)한다
     *
     * [처리 흐름]
     *
     *   사용자가 "참가 취소" 버튼 클릭
     *     → EnrollmentController.disenroll()
     *     → 이 메서드 호출
     *     → 신청 조회 → 출석 여부 확인 → 삭제 → 대기자 자동 승격
     *
     * [출석 체크된 신청은 취소 불가]
     *
     * isAttended()가 true인 경우, 이미 모임에 출석한 것이므로 취소할 수 없다.
     * 이는 "출석 후 취소하여 참가 기록을 조작하는 것"을 방지하기 위한 비즈니스 규칙이다.
     *
     * [대기자 자동 승격]
     *
     * FCFS(선착순) 방식에서 승인된 참가자가 취소하면 빈 자리가 1개 생긴다.
     * 이때 대기자 중 가장 먼저 신청한 1명을 자동으로 승인한다.
     *
     *   예시:
     *     정원: 5명, 승인: 5명(꽉참), 대기: 홍길동(1번째), 김철수(2번째)
     *     → 승인된 참가자 1명이 취소
     *     → 정원: 5명, 승인: 4명 (1자리 빔)
     *     → 홍길동 자동 승격 (accepted = true)
     *
     *   CONFIRMATIVE 방식에서는 자동 승격이 발생하지 않는다.
     *   (isAbleToAcceptWaitingEnrollment()에서 FCFS 조건 확인)
     *
     * @param event   참가 취소할 모임
     * @param account 참가 취소하는 사용자
     */
    public void cancelEnrollment(Event event, Account account) {

        // 해당 모임에서 해당 사용자의 참가 신청을 조회
        Enrollment enrollment = enrollmentRepository.findByEventAndAccount(event, account);

        // 이미 출석 처리된 경우 취소 불가
        if (!enrollment.isAttended()) {

            // 양방향 연관관계 해제 + DB에서 삭제
            event.removeEnrollment(enrollment);
            enrollmentRepository.delete(enrollment);

            // 빈 자리에 대기자 1명 자동 승격 (FCFS 방식인 경우에만 동작)
            event.acceptNextWaitingEnrollment();
        }
    }

    // ====================================================================
    // 운영자 관리 기능 (EnrollmentManageController에서 호출)
    // ====================================================================

    /**
     * 참가 신청을 승인한다 (CONFIRMATIVE 방식에서 운영자가 수동 승인)
     *
     * [처리 흐름]
     *
     *   운영자가 "승인" 버튼 클릭
     *     → EnrollmentManageController.acceptEnrollment()
     *     → 이 메서드 호출
     *     → 도메인 상태 전이 (accepted = true)
     *     → 승인 알림 이벤트 발행
     *
     * [도메인 로직 위임]
     *
     * 실제 승인 가능 여부 판단(모집 방식 확인, 정원 확인)은
     * Event.accept() 도메인 메서드 내부에서 수행된다.
     *
     *   Event.accept() 내부 로직:
     *     1. eventType == CONFIRMATIVE인지 확인
     *     2. limitOfEnrollments > getNumberOfAcceptedEnrollments()인지 확인
     *     3. 두 조건 모두 충족하면 enrollment.setAccepted(true) 실행
     *
     * Service는 도메인 메서드를 호출하고, 알림 이벤트를 발행하는 역할만 한다.
     * 이것이 도메인 주도 설계(DDD)에서 권장하는 "풍부한 도메인 모델(Rich Domain Model)" 패턴이다.
     *
     *   비교: 빈약한 도메인 모델(Anemic Domain Model)이었다면
     *   Service에서 직접 if문으로 조건을 확인하고 setAccepted(true)를 호출했을 것이다.
     *
     * [알림 이벤트 발행]
     *
     * 승인 처리 후 EnrollmentAcceptedEvent를 발행한다.
     * EnrollmentEventListener가 이 이벤트를 수신하여:
     *   - 사용자의 이메일 알림 설정이 켜져있으면 승인 안내 이메일 발송
     *   - 사용자의 웹 알림 설정이 켜져있으면 웹 알림 저장
     *
     * @param event      대상 모임 (모집 방식, 정원 확인에 사용)
     * @param enrollment 승인할 참가 신청
     */
    public void acceptEnrollment(Event event, Enrollment enrollment) {
        event.accept(enrollment);
        eventPublisher.publishEvent(new EnrollmentAcceptedEvent(enrollment));
    }

    /**
     * 참가 신청을 거절한다 (CONFIRMATIVE 방식에서 운영자가 수동 거절)
     *
     * [처리 흐름]
     *
     *   운영자가 "거절" 버튼 클릭
     *     → EnrollmentManageController.rejectEnrollment()
     *     → 이 메서드 호출
     *     → 도메인 상태 전이 (accepted = false)
     *     → 거절 알림 이벤트 발행
     *
     * [승인된 신청을 거절로 되돌리는 것]
     *
     * 이 메서드는 "이미 승인된 신청을 거절"하는 동작이다.
     * 아직 승인되지 않은(대기 중인) 신청은 canReject() 조건에서 걸러진다.
     *
     *   Event.canReject() 조건:
     *     - CONFIRMATIVE 방식
     *     - 이 Event의 신청 목록에 존재
     *     - 출석 처리되지 않음
     *     - 현재 승인 상태 (accepted = true)  ← 이미 승인된 것만 거절 가능
     *
     * @param event      대상 모임
     * @param enrollment 거절할 참가 신청
     */
    public void rejectEnrollment(Event event, Enrollment enrollment) {
        event.reject(enrollment);
        eventPublisher.publishEvent(new EnrollmentRejectedEvent(enrollment));
    }

    /**
     * 참가자의 출석을 체크한다
     *
     * [출석 체크의 의미]
     *
     * 모임 당일, 운영자가 실제로 참석한 사용자를 확인하여 출석 처리한다.
     * attended 필드는 참가 승인(accepted)과는 별개의 상태이다.
     *
     *   accepted: "참가할 수 있는 자격"이 있는가 (신청 시점에 결정)
     *   attended: "실제로 참석했는가" (모임 당일 운영자가 결정)
     *
     * [출석 체크의 비즈니스 효과]
     *
     * 출석 체크된 참가자는:
     *   1. 참가 신청 취소가 불가능하다 (cancelEnrollment에서 isAttended 확인)
     *   2. 승인/거절 상태 변경이 불가능하다 (canAccept/canReject에서 isAttended 확인)
     *   3. 마이페이지에서 "참석한 모임" 목록에 표시된다
     *
     * [JPA Dirty Checking으로 자동 반영]
     *
     * enrollment.setAttended(true)만 호출하면,
     * 트랜잭션 커밋 시점에 JPA가 변경을 감지하여 자동으로 UPDATE 쿼리를 실행한다.
     * 별도의 save() 호출이 필요 없다.
     *
     * @param enrollment 출석 체크할 참가 신청
     */
    public void checkInEnrollment(Enrollment enrollment) {
        enrollment.setAttended(true);
    }

    /**
     * 참가자의 출석 체크를 취소한다
     *
     * 실수로 출석 체크한 경우, 운영자가 되돌릴 수 있다.
     * attended를 false로 변경하면 해당 참가자는 다시:
     *   - 참가 신청 취소가 가능해진다
     *   - 승인/거절 상태 변경이 가능해진다
     *
     * @param enrollment 출석 체크를 취소할 참가 신청
     */
    public void cancelCheckInEnrollment(Enrollment enrollment) {
        enrollment.setAttended(false);
    }
}