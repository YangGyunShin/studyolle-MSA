package com.studyolle.modules.event.repository;

import com.studyolle.modules.account.entity.Account;
import com.studyolle.modules.event.entity.Enrollment;
import com.studyolle.modules.event.entity.Event;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 참가 신청(Enrollment) 엔티티의 데이터 접근 계층 (Repository)
 *
 * [이 Repository가 관리하는 엔티티 구조]
 *
 *   Enrollment --> Event --> Study
 *   (참가 신청)    (모임)    (스터디)
 *
 *   Enrollment --> Account
 *   (참가 신청)    (신청한 사용자)
 *
 *   하나의 Enrollment는 "어떤 사용자가 어떤 모임에 신청했는지"를 표현하므로,
 *   대부분의 조회 메서드가 Event + Account 조합을 파라미터로 받는다.
 *
 *
 * [@Transactional(readOnly = true) 설명]
 *
 * 이 Repository의 모든 메서드에 읽기 전용 트랜잭션이 적용된다.
 *
 *   효과:
 *   - JPA dirty checking(변경 감지) 비활성화 → 성능 최적화
 *   - 실수로 엔티티가 수정되어도 DB에 반영되지 않음 → 데이터 안전성 확보
 *
 *   쓰기 작업(save, delete)이 필요한 경우:
 *   - Service 계층에서 @Transactional을 선언하면 그 트랜잭션이 우선 적용되므로
 *     정상적으로 쓰기가 수행된다.
 */
@Transactional(readOnly = true)
public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    /**
     * 특정 이벤트에 특정 사용자가 이미 참가 신청했는지 여부를 확인
     *
     * [메서드 이름 기반 쿼리 자동 생성]
     *
     *   existsByEventAndAccount(Event event, Account account)
     *   │       │         │
     *   │       │         └── AndAccount → AND e.account = :account
     *   │       └── Event → WHERE e.event = :event
     *   └── existsBy → SELECT EXISTS(SELECT 1 FROM Enrollment e ...)
     *
     *   자동 생성되는 JPQL:
     *     SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END
     *     FROM Enrollment e
     *     WHERE e.event = :event AND e.account = :account
     *
     * [사용되는 곳]
     * EventService.newEnrollment() → 참가 신청 시 중복 신청 방지
     *
     *   if (!enrollmentRepository.existsByEventAndAccount(event, account)) {
     *       // 신청 내역이 없을 때만 새로 생성
     *   }
     *
     * [exists vs find 차이]
     * exists는 데이터 존재 여부만 확인하므로 실제 엔티티를 로딩하지 않는다.
     * 단순 중복 체크 용도에서는 find로 전체 엔티티를 가져오는 것보다 효율적이다.
     *
     *   exists → SELECT EXISTS(...)  → boolean 반환 (엔티티 로딩 없음)
     *   find   → SELECT * FROM ...   → Enrollment 객체 반환 (엔티티 로딩 발생)
     */
    boolean existsByEventAndAccount(Event event, Account account);

    /**
     * 특정 이벤트에서 특정 사용자의 참가 신청 엔티티를 조회
     *
     * [메서드 이름 기반 쿼리 자동 생성]
     *
     *   findByEventAndAccount(Event event, Account account)
     *   │      │         │
     *   │      │         └── AndAccount → AND e.account = :account
     *   │      └── Event → WHERE e.event = :event
     *   └── findBy → SELECT e FROM Enrollment e
     *
     *   자동 생성되는 JPQL:
     *     SELECT e FROM Enrollment e
     *     WHERE e.event = :event AND e.account = :account
     *
     * [사용되는 곳]
     * EventService.cancelEnrollment() → 참가 취소 시 삭제할 Enrollment를 찾기 위해 사용
     *
     *   Enrollment enrollment = enrollmentRepository.findByEventAndAccount(event, account);
     *   event.removeEnrollment(enrollment);      // 양방향 연관관계 해제
     *   enrollmentRepository.delete(enrollment);  // DB에서 삭제
     *
     * [반환값이 null일 수 있는 경우]
     * 해당 이벤트에 해당 사용자의 신청이 없으면 null을 반환한다.
     * 실제 코드에서는 existsByEventAndAccount()로 먼저 확인한 후 호출하거나,
     * 비즈니스 로직 상 신청이 반드시 존재하는 흐름에서만 호출된다.
     */
    Enrollment findByEventAndAccount(Event event, Account account);

    /**
     * 특정 사용자의 승인된 참가 신청 목록을 최신순으로 조회
     *
     * [메서드 이름 기반 쿼리 자동 생성]
     *
     *   findByAccountAndAcceptedOrderByEnrolledAtDesc(Account account, boolean accepted)
     *   │      │           │          │              │
     *   │      │           │          │              └── Desc → DESC (내림차순)
     *   │      │           │          └── OrderByEnrolledAt → ORDER BY e.enrolledAt
     *   │      │           └── AndAccepted → AND e.accepted = :accepted
     *   │      └── Account → WHERE e.account = :account
     *   └── findBy → SELECT e FROM Enrollment e
     *
     *   자동 생성되는 JPQL:
     *     SELECT e FROM Enrollment e
     *     WHERE e.account = :account AND e.accepted = :accepted
     *     ORDER BY e.enrolledAt DESC
     *
     *
     * [@EntityGraph("Enrollment.withEventAndStudy") 설명]
     *
     * 이 메서드는 사용자의 참가 신청 목록을 조회하면서, 각 신청이 어떤 모임의
     * 어떤 스터디에 속하는지도 함께 보여줘야 한다.
     *
     * EntityGraph 없이 조회하면 N+1 문제가 발생한다:
     *
     *   신청이 5건인 경우:
     *     쿼리 1: SELECT * FROM enrollment WHERE account_id = ? AND accepted = true
     *             (신청 5건 조회)
     *     쿼리 2: SELECT * FROM event WHERE id = 10     (1번 신청의 모임)
     *     쿼리 3: SELECT * FROM study WHERE id = 1      (1번 모임의 스터디)
     *     쿼리 4: SELECT * FROM event WHERE id = 20     (2번 신청의 모임)
     *     쿼리 5: SELECT * FROM study WHERE id = 2      (2번 모임의 스터디)
     *     ... (총 1 + 5 + 5 = 11번의 쿼리)
     *
     * @EntityGraph("Enrollment.withEventAndStudy")를 적용하면
     * Enrollment -> Event -> Study를 한 번의 JOIN 쿼리로 가져온다:
     *
     *   실제 실행되는 SQL:
     *     SELECT en.*, e.*, s.*
     *     FROM enrollment en
     *     JOIN event e ON en.event_id = e.id
     *     JOIN study s ON e.study_id = s.id
     *     WHERE en.account_id = ? AND en.accepted = true
     *     ORDER BY en.enrolled_at DESC
     *     → 단 1번의 쿼리로 모든 데이터 조회
     *
     * 이 EntityGraph는 Enrollment 엔티티에 다음과 같이 선언되어 있다:
     *
     *   @NamedEntityGraph(
     *       name = "Enrollment.withEventAndStudy",
     *       attributeNodes = @NamedAttributeNode(value = "event", subgraph = "study"),
     *       subgraphs = @NamedSubgraph(name = "study",
     *                       attributeNodes = @NamedAttributeNode("study"))
     *   )
     *
     *   → attributeNodes: Enrollment의 event 필드를 함께 조회
     *   → subgraph: Event 내부의 study 필드까지 2단계 깊이로 함께 조회
     *
     *
     * [EventRepository의 @EntityGraph와 비교]
     *
     *   EventRepository:
     *     @EntityGraph("Event.withEnrollments")
     *     → Event -> Enrollments (1단계, subgraph 불필요)
     *     → 모임 목록에서 참가자 수를 보여줄 때 사용
     *
     *   EnrollmentRepository (현재):
     *     @EntityGraph("Enrollment.withEventAndStudy")
     *     → Enrollment -> Event -> Study (2단계, subgraph 필요)
     *     → 사용자의 참가 내역에서 "어떤 스터디의 어떤 모임"인지 보여줄 때 사용
     *
     *
     * [사용되는 곳]
     * 주로 사용자의 마이페이지나 대시보드에서 "내가 참가 확정된 모임 목록"을 보여줄 때 사용
     *
     *   List<Enrollment> enrollments =
     *       enrollmentRepository.findByAccountAndAcceptedOrderByEnrolledAtDesc(account, true);
     *   → accepted=true: 승인된 신청만 조회 (대기 중인 신청 제외)
     *   → OrderByEnrolledAtDesc: 최근 신청한 것이 위에 오도록 정렬
     */
    @EntityGraph("Enrollment.withEventAndStudy")
    List<Enrollment> findByAccountAndAcceptedOrderByEnrolledAtDesc(Account account, boolean accepted);
}