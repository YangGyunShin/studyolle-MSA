package com.studyolle.modules.event.entity;

import com.studyolle.modules.account.entity.Account;
import com.studyolle.modules.study.entity.Study;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 이벤트(모임) 엔티티
 *
 * - 스터디(Study) 내에서 개최되는 개별 모임을 표현하는 핵심 도메인 객체
 * - 모임의 기본 정보(제목, 설명, 일시)와 참가 관리(모집 방식, 정원, 참가 신청 목록)를 관리
 *
 * [엔티티 연관관계 구조]
 *
 *   Study (1) <--- (*) Event (1) <--- (*) Enrollment
 *   (스터디)          (모임)              (참가 신청)
 *
 *   - 하나의 스터디에 여러 모임이 존재할 수 있고,
 *   - 하나의 모임에 여러 참가 신청이 존재할 수 있다.
 *
 *   예) Study("Java 스터디")
 *        ├── Event("1월 정기모임")
 *        │    ├── Enrollment(홍길동, 승인됨)
 *        │    └── Enrollment(김철수, 대기중)
 *        └── Event("2월 특별 세미나")
 *             └── Enrollment(이영희, 승인됨)
 *
 *
 * [모집 방식 (EventType)]
 *
 * 이벤트는 두 가지 모집 방식을 지원하며, 방식에 따라 참가 승인 흐름이 달라진다:
 *
 *   1. FCFS (First Come First Served, 선착순)
 *      - 참가 신청 시점에 정원이 남아있으면 즉시 자동 승인 (accepted = true)
 *      - 정원이 꽉 차면 대기 상태로 등록 (accepted = false)
 *      - 기존 참가자가 취소하면 대기자 중 가장 먼저 신청한 사람이 자동 승격
 *
 *      흐름: 신청 -> 정원 확인 -> 남아있으면 즉시 승인 / 꽉 찼으면 대기
 *
 *   2. CONFIRMATIVE (관리자 확인)
 *      - 참가 신청 시 무조건 대기 상태로 등록 (accepted = false)
 *      - 스터디 운영자(리더)가 직접 승인/거절 처리
 *      - 자동 승격 없음 (모든 승인이 수동)
 *
 *      흐름: 신청 -> 대기 -> 운영자가 승인 또는 거절
 *
 *
 * [@NamedEntityGraph 설명]
 *
 * Event를 조회할 때 enrollments(참가 신청 목록)는 기본적으로 LAZY 로딩이다.
 * 즉, event.getEnrollments()를 호출하는 시점에 추가 쿼리가 발생한다.
 *
 * 모임 상세 페이지에서는 참가자 목록을 항상 함께 보여줘야 하므로,
 * @NamedEntityGraph로 "Event를 조회할 때 enrollments도 한 번에 가져와라"고 설정한다.
 *
 *   @NamedEntityGraph(
 *       name = "Event.withEnrollments",
 *          --> 이 설정의 이름표. Repository에서 @EntityGraph("Event.withEnrollments")로 참조한다.
 *
 *       attributeNodes = @NamedAttributeNode("enrollments")
 *          --> Event의 enrollments 필드를 함께 조회한다.
 *   )
 *
 * 결과적으로 Event + 모든 Enrollment를 한 번의 JOIN 쿼리로 가져온다:
 *
 *   SELECT e.*, en.*
 *   FROM event e
 *   LEFT JOIN enrollment en ON e.id = en.event_id
 *   WHERE e.id = ?
 *
 * Enrollment 엔티티의 @NamedEntityGraph("Enrollment.withEventAndStudy")와 비교:
 *   - Enrollment 쪽: Enrollment -> Event -> Study (2단계, subgraph 필요)
 *   - Event 쪽: Event -> Enrollments (1단계, subgraph 불필요)
 *
 * [실제 사용 예시 - Repository에서 이름표로 참조]
 *
 *   @EntityGraph("Event.withEnrollments")
 *   List<Event> findByStudy(Study study);
 */
@NamedEntityGraph(
        name = "Event.withEnrollments",
        attributeNodes = @NamedAttributeNode("enrollments")
)
@Entity
@Getter
@Setter
@EqualsAndHashCode(of = "id")
@NoArgsConstructor
public class Event {

    @Id @GeneratedValue
    private Long id;

    /** 이 모임이 속한 스터디 - Event : Study = N : 1 */
    @ManyToOne
    private Study study;

    /** 모임을 만든 사용자(스터디 운영자) - Event : Account = N : 1 */
    @ManyToOne
    private Account createdBy;

    /** 모임 제목 */
    @Column(nullable = false)
    private String title;

    /** 모임 상세 설명 (긴 텍스트 저장을 위해 @Lob 사용) */
    @Lob
    private String description;

    /** 모임 생성 일시 (모임이 DB에 저장된 시점) */
    @Column(nullable = false)
    private LocalDateTime createdDateTime;

    /** 참가 신청 마감 일시 (이 시점 이후에는 신청/취소 불가) */
    @Column(nullable = false)
    private LocalDateTime endEnrollmentDateTime;

    /** 모임 시작 일시 */
    @Column(nullable = false)
    private LocalDateTime startDateTime;

    /** 모임 종료 일시 (이 시점 기준으로 지난 모임/예정 모임 분류) */
    @Column(nullable = false)
    private LocalDateTime endDateTime;

    /** 모집 정원 (null이면 제한 없음) */
    @Column
    private Integer limitOfEnrollments;

    /**
     * 이 모임에 대한 참가 신청 목록
     *
     * - Event : Enrollment = 1 : N (양방향)
     * - mappedBy = "event": FK의 주인은 Enrollment 쪽이다.
     *   즉, DB에서 enrollment 테이블의 event_id 컬럼이 실제 FK이며,
     *   이 리스트는 "읽기 전용 역참조"로, JPA가 Enrollment.event를 기준으로 자동 매핑해준다.
     * - @OrderBy("enrolledAt"): 신청 시각 순으로 정렬하여 선착순 처리에 활용
     *
     * [cascade = CascadeType.REMOVE]
     *
     *  부모 엔티티(Event)가 삭제될 때, 자식 엔티티(Enrollment)도 함께 삭제되도록 설정한다.
     *
     *  이 설정이 없으면 Event를 삭제할 때 Enrollment의 event_id FK가 이미 삭제된 Event를 참조하게 되어 참조 무결성 위반 에러가 발생한다.
     *
     *   설정 없을 때:
     *     eventRepository.delete(event)
     *       → DELETE FROM event WHERE id = 10
     *       → Enrollment(event_id=10)가 여전히 존재
     *       → DataIntegrityViolationException 발생
     *
     *   설정 있을 때:
     *     eventRepository.delete(event)
     *       → DELETE FROM enrollment WHERE event_id = 10  (자식 먼저 삭제)
     *       → DELETE FROM event WHERE id = 10             (부모 삭제)
     *       → 정상 처리
     *
     * [CascadeType.ALL이 아닌 REMOVE만 지정한 이유]
     *
     *   CascadeType.ALL에는 PERSIST, MERGE, REMOVE 등이 모두 포함된다.
     *
     *   CascadeType.PERSIST를 설정하면, 이미 영속 상태인 Event의 enrollments 컬렉션에
     *   새로운(비영속) Enrollment를 추가하는 것만으로 JPA가 해당 Enrollment를 자동 저장한다.
     *
     *   현재 EnrollmentService.newEnrollment()의 코드를 보면:
     *
     *     event.addEnrollment(enrollment);       // ① 영속 Event의 컬렉션에 비영속 Enrollment 추가
     *     enrollmentRepository.save(enrollment); // ② 명시적으로 Enrollment 저장
     *
     *   여기서 Event는 DB에서 조회한 영속 상태 엔티티이고,
     *   Enrollment는 new로 생성한 비영속 상태 엔티티이다.
     *
     *   만약 CascadeType.PERSIST가 설정되어 있다면:
     *     - ①에서 영속 Event의 컬렉션에 비영속 Enrollment가 추가되는 순간 JPA가 cascade를 발동하여 Enrollment를 자동으로 persist 처리한다
     *     - ②의 enrollmentRepository.save()가 없어도 Enrollment가 저장된다
     *     - 이렇게 되면 Enrollment의 저장이 Event를 통해 암묵적으로 일어나므로 코드만 보고 "Enrollment가 언제 저장되는지" 파악하기 어려워진다
     *
     *   현재는 PERSIST를 설정하지 않았으므로:
     *     - ①은 순수하게 메모리상의 컬렉션 추가 + FK 설정만 수행
     *     - ②에서 명시적으로 save()를 호출해야만 Enrollment가 DB에 저장된다
     *     - Enrollment의 저장 시점이 코드에 명확하게 드러난다
     *
     *   따라서 삭제 전파(REMOVE)만 필요하므로 REMOVE만 설정하여 의도를 명확히 한다.
     *
     *
     * [orphanRemoval = true]
     *
     *  부모(Event)의 컬렉션에서 자식(Enrollment)이 제거되면, 해당 자식을 DB에서도 자동으로 DELETE 처리한다.
     *
     *  "고아(orphan)"란 부모와의 연관관계가 끊어져서 더 이상 어디에서도 참조되지 않는 자식 엔티티를 의미한다.
     *
     *   orphanRemoval 동작 예시:
     *     event.getEnrollments().remove(enrollment)  // 컬렉션에서 제거
     *       → JPA가 enrollment를 "고아"로 인식
     *       → 트랜잭션 커밋 시 DELETE FROM enrollment WHERE id = ? 자동 실행
     *
     *  이 프로젝트에서 orphanRemoval이 필요한 이유:
     *    Event.removeEnrollment() 메서드에서 this.enrollments.remove(enrollment)를 호출하는데,
     *    orphanRemoval이 없으면 컬렉션에서는 빠지지만 DB에는 남아있는 불일치가 발생할 수 있다.
     *
     *
     * [CascadeType.REMOVE vs orphanRemoval 차이]
     *
     *   CascadeType.REMOVE:
     *     - "부모가 삭제될 때" 자식도 함께 삭제
     *     - 부모 엔티티 자체의 삭제에 반응
     *     - eventRepository.delete(event) → Enrollment도 삭제
     *
     *   orphanRemoval = true:
     *     - "부모의 컬렉션에서 제거될 때" 해당 자식을 삭제
     *     - 연관관계 해제에 반응 (부모가 살아있어도 동작)
     *     - event.getEnrollments().remove(enrollment) → 해당 Enrollment 삭제
     *
     *   둘 다 설정한 이유:
     *     - REMOVE: Event 삭제 시 모든 Enrollment 일괄 삭제 (deleteEvent)
     *     - orphanRemoval: 개별 Enrollment 제거 시 자동 삭제 (removeEnrollment)
     *     - 두 시나리오 모두 커버하기 위해 함께 설정
     */
    @OneToMany(mappedBy = "event", cascade = CascadeType.REMOVE, orphanRemoval = true)
    @OrderBy("enrolledAt")
    private List<Enrollment> enrollments = new ArrayList<>();

    /**
     * 모집 방식
     *
     * - FCFS: 선착순 자동 승인
     * - CONFIRMATIVE: 관리자 수동 승인
     * - @Enumerated(EnumType.STRING): DB에 "FCFS", "CONFIRMATIVE" 문자열로 저장
     *   (EnumType.ORDINAL은 순서가 바뀌면 데이터가 꼬이므로 STRING 사용)
     */
    @Enumerated(EnumType.STRING)
    private EventType eventType;

    // ====================================================================
    // 참가 신청 가능 여부 판단 메서드 (뷰에서 버튼 활성화/비활성화에 활용)
    // ====================================================================

    /**
     * 해당 사용자가 이 모임에 참가 신청할 수 있는지 판단
     *
     * 참가 신청 버튼이 활성화되려면 아래 3가지 조건을 모두 충족해야 한다:
     *   1. 신청 마감 전이어야 함 (isNotClosed)
     *   2. 아직 출석 처리되지 않았어야 함 (이미 출석한 모임에 다시 신청하는 건 의미 없음)
     *   3. 아직 신청하지 않았어야 함 (중복 신청 방지)
     *
     * @param userAccount 현재 로그인한 사용자의 인증 정보 래퍼
     *                    (Spring Security의 UserDetails 구현체로, 내부에 Account 엔티티를 담고 있다)
     */
    public boolean isEnrollableFor(UserAccount userAccount) {
        return isNotClosed() && !isAttended(userAccount) && !isAlreadyEnrolled(userAccount);
    }

    /**
     * 해당 사용자가 이 모임의 참가 신청을 취소할 수 있는지 판단
     *
     * 참가 취소 버튼이 활성화되려면 아래 3가지 조건을 모두 충족해야 한다:
     *   1. 신청 마감 전이어야 함 (마감 후에는 취소 불가)
     *   2. 아직 출석 처리되지 않았어야 함 (이미 출석한 건 취소 불가)
     *   3. 이미 신청한 상태여야 함 (신청하지 않은 걸 취소할 수는 없음)
     *
     * isEnrollableFor()와 비교하면 3번 조건만 반대:
     *   - 신청 가능: !isAlreadyEnrolled (신청 안 한 상태)
     *   - 취소 가능: isAlreadyEnrolled (신청 한 상태)
     */
    public boolean isDisenrollableFor(UserAccount userAccount) {
        return isNotClosed() && !isAttended(userAccount) && isAlreadyEnrolled(userAccount);
    }

    /**
     * 참가 신청 마감 여부 확인
     *
     * - endEnrollmentDateTime이 현재 시각보다 미래이면 아직 마감 전 (true)
     * - endEnrollmentDateTime이 현재 시각보다 과거이면 이미 마감됨 (false)
     */
    private boolean isNotClosed() {
        return this.endEnrollmentDateTime.isAfter(LocalDateTime.now());
    }

    /**
     * 해당 사용자가 이 모임에 출석했는지 확인
     *
     * - enrollments 목록을 순회하면서 해당 사용자의 Enrollment를 찾고,
     *   그 Enrollment의 attended 값이 true인지 확인
     * - 출석한 참가자는 신청 취소가 불가능하다 (이미 참석한 모임이므로)
     */
    public boolean isAttended(UserAccount userAccount) {
        Account account = userAccount.getAccount();
        for (Enrollment e : this.enrollments) {
            if (e.getAccount().equals(account) && e.isAttended()) {
                return true;
            }
        }

        return false;
    }

    /**
     * 남은 모집 자리 수 계산
     *
     * - 전체 모집 정원에서 현재 승인된 참가자 수를 빼서 반환
     * - 뷰에서 "남은 자리: 3/10" 같은 표시에 활용
     *
     * 계산 예시:
     *   정원(limitOfEnrollments) = 10
     *   승인된 참가자(accepted=true) = 7명
     *   --> 남은 자리 = 10 - 7 = 3
     */
    public int numberOfRemainSpots() {
        return this.limitOfEnrollments - (int) this.enrollments.stream().filter(Enrollment::isAccepted).count();
    }

    /**
     * 해당 사용자가 이미 이 모임에 참가 신청했는지 확인
     *
     * - enrollments 목록에서 해당 Account와 일치하는 Enrollment가 있는지 확인
     * - 승인 여부(accepted)와 관계없이, 신청 자체가 존재하면 true 반환
     *   (대기 중이든 승인됐든 이미 신청한 상태)
     */
    private boolean isAlreadyEnrolled(UserAccount userAccount) {
        Account account = userAccount.getAccount();
        for (Enrollment e : this.enrollments) {
            if (e.getAccount().equals(account)) {
                return true;
            }
        }
        return false;
    }

    // ====================================================================
    // 참가 인원 조회 메서드
    // ====================================================================

    /**
     * 현재까지 승인된 참가자 수를 반환
     *
     * - accepted == true인 Enrollment 수를 카운트
     * - 모집 정원과 비교하여 추가 승인 가능 여부 판단에 활용
     * - FCFS 자동 승인, CONFIRMATIVE 수동 승인 모두에서 공통으로 사용
     */
    public long getNumberOfAcceptedEnrollments() {
        return this.enrollments.stream()
                .filter(Enrollment::isAccepted)
                .count();
    }

    // ====================================================================
    // 양방향 연관관계 편의 메서드
    // ====================================================================

    /**
     * 참가 신청(Enrollment)을 이 모임에 추가하는 양방향 연관관계 편의 메서드
     *
     * JPA에서 양방향 연관관계를 설정할 때는 반드시 양쪽 모두에 관계를 설정해야 한다.
     * 한쪽만 설정하면 다음과 같은 문제가 발생한다:
     *
     *   - this.enrollments.add(enrollment)만 호출한 경우:
     *     메모리상의 리스트에는 추가되지만, Enrollment 테이블의 event_id FK는 설정되지 않는다.
     *     DB에 저장할 때 FK가 null이 되어 연관관계가 끊어진다.
     *
     *   - enrollment.setEvent(this)만 호출한 경우:
     *     DB의 FK는 정상 설정되지만, 메모리상의 enrollments 리스트에는 반영되지 않는다.
     *     같은 트랜잭션 안에서 event.getEnrollments()를 조회하면 방금 추가한 신청이 빠져있다.
     *
     * 따라서 이 메서드에서 양쪽을 동시에 설정하여 DB와 메모리 모두 일관성을 보장한다.
     */
    public void addEnrollment(Enrollment enrollment) {
        this.enrollments.add(enrollment);   // 비주인 측(Event): 메모리 리스트에 추가
        enrollment.setEvent(this);          // 주인 측(Enrollment): DB FK 설정
    }

    /**
     * 참가 신청(Enrollment)을 이 모임에서 제거하는 양방향 연관관계 편의 메서드
     *
     * 제거 시에도 양쪽 모두 관계를 해제해야 한다.
     *
     *   - this.enrollments.remove(enrollment)만 호출한 경우:
     *     메모리 리스트에서는 빠지지만, Enrollment 엔티티는 여전히 event_id FK를 가지고 있어
     *     DB에서는 연관관계가 유지된 상태로 남는다.
     *
     *   - enrollment.setEvent(null)만 호출한 경우:
     *     FK는 해제되지만, 메모리상의 enrollments 리스트에는 여전히 남아있어
     *     이후 로직에서 이미 제거된 신청이 포함된 채로 처리될 수 있다.
     *
     * 특히 orphanRemoval = true 설정이 있을 경우,
     * 양쪽 모두 정확히 해제되어야 JPA가 고아 엔티티로 인식하고 자동 삭제한다.
     */
    public void removeEnrollment(Enrollment enrollment) {
        this.enrollments.remove(enrollment);  // 비주인 측(Event): 메모리 리스트에서 제거
        enrollment.setEvent(null);            // 주인 측(Enrollment): DB FK 해제
    }

    // ====================================================================
    // FCFS(선착순) 자동 승인 관련 메서드
    // ====================================================================

    /**
     * 현재 신규 참가 신청을 자동 승인할 수 있는 상태인지 판단
     *
     * 자동 승인이 가능하려면 두 가지 조건을 동시에 충족해야 한다:
     *   1. 모집 방식이 FCFS(선착순)이어야 함
     *      - CONFIRMATIVE(관리자 확인) 방식은 자동 승인 대상이 아님
     *   2. 현재 승인된 인원이 모집 정원보다 적어야 함
     *      - 정원이 꽉 찼으면 자동 승인 불가 -> 대기 상태로 등록
     *
     * 이 메서드는 두 곳에서 활용된다:
     *   - EventService.newEnrollment(): 신규 신청 시 즉시 승인 여부 결정
     *   - acceptWaitingList() / acceptNextWaitingEnrollment(): 취소 발생 시 대기자 자동 승격 여부 결정
     */
    public boolean isAbleToAcceptWaitingEnrollment() {
        return this.eventType == EventType.FCFS && this.limitOfEnrollments > this.getNumberOfAcceptedEnrollments();
    }

    /**
     * 대기자 일괄 자동 승인 처리
     *
     * 모집 정원에 비해 승인된 인원이 부족할 때, 대기자 명단에서 부족한 만큼 자동으로 승인한다.
     * 주로 모집 정원이 늘어났을 때(이벤트 수정 시) 호출된다.
     *
     * 처리 흐름:
     *   1. FCFS 방식이고 정원에 여유가 있는지 확인
     *   2. 대기자 목록 추출 (accepted=false인 신청자들)
     *   3. 남은 정원 vs 대기자 수 중 더 적은 수만큼 승인
     *   4. 대기자 리스트 앞에서부터(선착순) 승인 처리
     *
     * 예시:
     *   정원: 10명, 승인: 7명, 대기: 5명
     *   -> 남은 정원 = 3, 대기자 = 5 -> min(3, 5) = 3명 승인
     *   -> 대기자 중 가장 먼저 신청한 3명이 자동 승인됨
     */
    public void acceptWaitingList() {
        if (this.isAbleToAcceptWaitingEnrollment()) {
            var waitingList = getWaitingList();
            int numberToAccept = (int) Math.min(
                    this.limitOfEnrollments - this.getNumberOfAcceptedEnrollments(),
                    waitingList.size()
            );
            waitingList.subList(0, numberToAccept).forEach(e -> e.setAccepted(true));
        }
    }

    /**
     * 대기자 1명 자동 승격 처리
     *
     * 기존 참가자가 취소했을 때 호출되어, 대기자 중 가장 먼저 신청한 1명을 자동 승인한다.
     * acceptWaitingList()가 "일괄 승인"이라면, 이 메서드는 "1명씩 승인"이다.
     *
     * 사용 시점:
     *   - EventService.cancelEnrollment() 내부에서 참가 취소 후 호출
     *   - 취소로 빈 자리가 1개 생겼으므로 대기자 1명만 승격하면 충분
     *
     * 처리 흐름:
     *   1. FCFS 방식이고 정원에 여유가 있는지 확인
     *   2. 대기자 중 가장 먼저 신청한 1명 조회
     *   3. 해당 신청자를 승인 처리 (accepted = true)
     */
    public void acceptNextWaitingEnrollment() {
        if (this.isAbleToAcceptWaitingEnrollment()) {
            Enrollment enrollmentToAccept = this.getTheFirstWaitingEnrollment();
            if (enrollmentToAccept != null) {
                enrollmentToAccept.setAccepted(true);
            }
        }
    }

    /**
     * 대기자 목록 반환 (승인되지 않은 참가 신청자들)
     *
     * - accepted == false인 Enrollment만 필터링
     * - enrollments 리스트가 @OrderBy("enrolledAt")으로 정렬되어 있으므로,
     *   반환되는 대기자 리스트도 신청 순서가 유지된다
     */
    private List<Enrollment> getWaitingList() {
        return this.enrollments.stream()
                .filter(enrollment -> !enrollment.isAccepted())
                .collect(Collectors.toList());
    }

    /**
     * 가장 먼저 대기 중인 참가 신청 1건 반환
     *
     * - enrollments가 신청 시각순으로 정렬되어 있으므로,
     *   accepted=false인 첫 번째 항목이 가장 오래 기다린 대기자
     * - 대기자가 없으면 null 반환
     */
    private Enrollment getTheFirstWaitingEnrollment() {
        for (Enrollment e : this.enrollments) {
            if (!e.isAccepted()) {
                return e;
            }
        }

        return null;
    }

    // ====================================================================
    // CONFIRMATIVE(관리자 확인) 승인/거절 관련 메서드
    // ====================================================================

    /**
     * 관리자가 해당 참가 신청을 승인할 수 있는지 판단
     *
     * 승인 가능 조건 (모두 충족해야 함):
     *   1. 모집 방식이 CONFIRMATIVE여야 함 (FCFS는 자동 승인이라 수동 승인 불필요)
     *   2. 해당 Enrollment가 이 Event의 신청 목록에 존재해야 함
     *   3. 현재 승인 인원이 정원 미만이어야 함 (정원 초과 승인 방지)
     *   4. 아직 출석 처리되지 않았어야 함 (이미 출석한 건 상태 변경 불가)
     *   5. 아직 승인되지 않았어야 함 (이미 승인된 건 다시 승인할 필요 없음)
     */
    public boolean canAccept(Enrollment enrollment) {
        return this.eventType == EventType.CONFIRMATIVE
                && this.enrollments.contains(enrollment)
                && this.limitOfEnrollments > this.getNumberOfAcceptedEnrollments()
                && !enrollment.isAttended()
                && !enrollment.isAccepted();
    }

    /**
     * 관리자가 해당 참가 신청을 거절할 수 있는지 판단
     *
     * 거절 가능 조건 (모두 충족해야 함):
     *   1. 모집 방식이 CONFIRMATIVE여야 함
     *   2. 해당 Enrollment가 이 Event의 신청 목록에 존재해야 함
     *   3. 아직 출석 처리되지 않았어야 함 (이미 출석한 건 거절 불가)
     *   4. 현재 승인된 상태여야 함 (승인된 신청을 거절하는 것이므로)
     *
     * canAccept()와 비교하면 마지막 조건이 반대:
     *   - 승인 가능: !enrollment.isAccepted() (아직 미승인 -> 승인 가능)
     *   - 거절 가능: enrollment.isAccepted() (이미 승인됨 -> 거절 가능)
     */
    public boolean canReject(Enrollment enrollment) {
        return this.eventType == EventType.CONFIRMATIVE
                && this.enrollments.contains(enrollment)
                && !enrollment.isAttended()
                && enrollment.isAccepted();
    }

    /**
     * 참가 신청 승인 처리 (도메인 상태 전이)
     *
     * - CONFIRMATIVE 방식에서 운영자가 대기 중인 신청자를 수동 승인할 때 호출
     * - 정원을 초과하지 않는 경우에만 승인 처리
     * - JPA dirty checking에 의해 트랜잭션 커밋 시 자동으로 DB에 반영됨
     *   (별도의 save() 호출이 필요 없음)
     */
    public void accept(Enrollment enrollment) {
        if (this.eventType == EventType.CONFIRMATIVE
                && this.limitOfEnrollments > this.getNumberOfAcceptedEnrollments()) {
            enrollment.setAccepted(true);
        }
    }

    /**
     * 참가 신청 거절 처리 (도메인 상태 전이)
     *
     * - CONFIRMATIVE 방식에서 운영자가 승인된 신청자를 거절할 때 호출
     * - accepted를 false로 변경하여 대기 상태로 되돌림
     * - JPA dirty checking에 의해 트랜잭션 커밋 시 자동으로 DB에 반영됨
     */
    public void reject(Enrollment enrollment) {
        if (this.eventType == EventType.CONFIRMATIVE) {
            enrollment.setAccepted(false);
        }
    }
}