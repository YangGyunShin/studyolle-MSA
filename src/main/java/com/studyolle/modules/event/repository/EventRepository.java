package com.studyolle.modules.event.repository;

import com.studyolle.modules.event.entity.Event;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 이벤트(모임) 엔티티의 데이터 접근 계층 (Repository)
 *
 * [Spring Data JPA Repository란?]
 *
 * JpaRepository<Event, Long>을 상속하면, 직접 SQL이나 JPQL을 작성하지 않아도
 * Spring Data JPA가 기본적인 CRUD 메서드를 자동으로 구현체를 생성해준다.
 *
 *   자동 제공되는 주요 메서드:
 *     save(Event entity)       → INSERT 또는 UPDATE (id 존재 여부에 따라 자동 판단)
 *     findById(Long id)        → SELECT * FROM event WHERE id = ?
 *     findAll()                → SELECT * FROM event
 *     delete(Event entity)     → DELETE FROM event WHERE id = ?
 *     count()                  → SELECT COUNT(*) FROM event
 *     existsById(Long id)      → SELECT EXISTS(...)
 *
 *   제네릭 파라미터:
 *     - Event: 이 Repository가 관리하는 엔티티 타입
 *     - Long: 해당 엔티티의 PK(@Id) 타입
 *
 * [인터페이스만 선언하면 동작하는 원리]
 *
 * Spring Data JPA는 애플리케이션 시작 시점에 이 인터페이스를 스캔하고,
 * JDK 동적 프록시(Dynamic Proxy)를 사용하여 구현체를 런타임에 자동 생성한다.
 * 따라서 개발자는 인터페이스에 메서드 시그니처만 선언하면 된다.
 *
 * 커스텀 메서드(예: findByStudyOrderByStartDateTime)도 메서드 이름 규칙에 따라
 * Spring이 자동으로 JPQL을 생성해준다. (쿼리 메서드 네이밍 전략)
 *
 *
 * [@Transactional(readOnly = true) 설명]
 *
 * 인터페이스 레벨에 선언하면 이 Repository의 모든 메서드에 읽기 전용 트랜잭션이 적용된다.
 *
 *   읽기 전용 트랜잭션의 이점:
 *     1. JPA dirty checking(변경 감지)을 수행하지 않아 메모리와 CPU 절약
 *        → 일반 트랜잭션: 조회 후 엔티티의 모든 필드 변경 여부를 스냅샷과 비교
 *        → 읽기 전용: 스냅샷 생성 자체를 건너뜀
 *     2. DB 레벨에서 읽기 전용 힌트를 전달하여 DB 최적화 가능 (DB 종류에 따라 다름)
 *     3. 실수로 엔티티를 수정해도 DB에 반영되지 않아 데이터 안전성 확보
 *
 *   주의: 쓰기 작업(save, delete 등)은 Service 계층에서 @Transactional을 별도 선언하므로
 *   그 트랜잭션이 우선 적용된다. (가장 가까운 @Transactional이 적용되는 원칙)
 */
@Transactional(readOnly = true)
public interface EventRepository extends JpaRepository<Event, Long> {

    /**
     * 특정 스터디에 속한 모든 이벤트를 시작일 기준 오름차순으로 조회
     *
     * [메서드 이름 기반 쿼리 자동 생성 (Query Method)]
     *
     * Spring Data JPA는 메서드 이름을 파싱하여 자동으로 JPQL을 생성한다:
     *
     *   findByStudyOrderByStartDateTime(Study study)
     *   │     │         │
     *   │     │         └── OrderByStartDateTime → ORDER BY e.startDateTime ASC
     *   │     └── Study → WHERE e.study = :study
     *   └── findBy → SELECT e FROM Event e
     *
     *   자동 생성되는 JPQL:
     *     SELECT e FROM Event e WHERE e.study = :study ORDER BY e.startDateTime ASC
     *
     *
     * [@EntityGraph 설명]
     *
     * 위 JPQL만으로는 Event만 조회되고, 각 Event의 enrollments(참가 신청 목록)는
     * LAZY 로딩이다. 이벤트 목록 화면에서 각 이벤트의 참가자 수를 보여주려면
     * event.getEnrollments()를 호출해야 하는데, 이때 이벤트 건수만큼 추가 쿼리가 발생한다.
     *
     *   N+1 문제 예시 (이벤트가 5개인 경우):
     *     쿼리 1: SELECT * FROM event WHERE study_id = ?         (이벤트 5개 조회)
     *     쿼리 2: SELECT * FROM enrollment WHERE event_id = 1    (1번 이벤트의 참가 신청)
     *     쿼리 3: SELECT * FROM enrollment WHERE event_id = 2    (2번 이벤트의 참가 신청)
     *     쿼리 4: SELECT * FROM enrollment WHERE event_id = 3    (3번 이벤트의 참가 신청)
     *     쿼리 5: SELECT * FROM enrollment WHERE event_id = 4    (4번 이벤트의 참가 신청)
     *     쿼리 6: SELECT * FROM enrollment WHERE event_id = 5    (5번 이벤트의 참가 신청)
     *     → 총 6번의 쿼리 실행 (1 + N 문제)
     *
     * @EntityGraph를 적용하면 한 번의 JOIN 쿼리로 Event + Enrollment를 함께 가져온다:
     *
     *   실제 실행되는 SQL:
     *     SELECT e.*, en.*
     *     FROM event e
     *     LEFT JOIN enrollment en ON e.id = en.event_id
     *     WHERE e.study_id = ?
     *     ORDER BY e.start_date_time ASC
     *     → 단 1번의 쿼리로 모든 데이터 조회
     *
     * @EntityGraph 속성 설명:
     *   - value = "Event.withEnrollments"
     *     → Event 엔티티에 선언된 @NamedEntityGraph의 이름을 참조
     *     → 해당 EntityGraph에서 enrollments 필드를 fetch 대상으로 지정해둠
     *
     *   - type = EntityGraphType.LOAD
     *     → EntityGraph에 명시된 필드(enrollments)는 EAGER로 로딩하고,
     *       나머지 필드는 엔티티에 선언된 기본 전략(보통 LAZY)을 따른다.
     *     → 비교: EntityGraphType.FETCH는 명시된 필드만 EAGER, 나머지는 모두 LAZY 강제
     *
     *
     * [사용되는 곳]
     * EventController.viewStudyEvents() → 스터디의 이벤트 목록 페이지
     */
    @EntityGraph(value = "Event.withEnrollments", type = EntityGraph.EntityGraphType.LOAD)
    List<Event> findByStudyOrderByStartDateTime(Study study);

    /**
     * 여러 스터디에 속한 예정된 모임을 한 번에 조회
     *
     * [사용 목적]
     * 홈 화면(index-after-login)의 "내 스터디" 사이드바에서
     * 각 스터디 하위에 예정된 모임을 표시하기 위해 사용한다.
     *
     * [N+1 문제 방지]
     * 스터디 목록을 순회하면서 개별 조회하면 스터디 수만큼 쿼리가 발생하므로,
     * IN 절을 사용하여 한 번의 쿼리로 모든 스터디의 예정 모임을 가져온다.
     *
     * 자동 생성되는 JPQL:
     *   SELECT e FROM Event e
     *   WHERE e.study IN :studies AND e.endDateTime > :now
     *   ORDER BY e.startDateTime ASC
     *
     * @param studies 조회 대상 스터디 목록 (관리중 + 참여중)
     * @param now     현재 시각 (이 시각 이후에 종료되는 모임만 조회)
     * @return 예정된 모임 목록 (시작일 오름차순)
     */
    List<Event> findByStudyInAndEndDateTimeAfterOrderByStartDateTimeAsc(List<Study> studies, LocalDateTime now);

    /**
     * 캘린더 뷰용: 특정 스터디 목록에 속한 이벤트를 날짜 범위로 조회
     *
     * FullCalendar.js가 월간/주간 뷰를 전환할 때마다
     * ?start=2025-03-01T00:00:00&end=2025-04-01T00:00:00 형태로 요청하므로,
     * 해당 범위에 걸치는 모임만 효율적으로 조회한다.
     *
     * [날짜 범위 필터링 로직]
     *
     * 모임의 시작일(startDateTime)이 요청 범위의 끝(end) 이전이고,
     * 모임의 종료일(endDateTime)이 요청 범위의 시작(start) 이후인 모임을 조회.
     * 이 조건은 "두 구간이 겹치는지" 판별하는 표준 알고리즘이다:
     *
     *   구간 A [start, end)와 구간 B [startDateTime, endDateTime]이 겹치려면:
     *     A.start < B.end AND A.end > B.start
     *
     *   → startDateTime < end AND endDateTime > start
     *
     * 이 방식의 장점:
     *   - 3월 28일~4월 2일에 걸친 모임도 3월 뷰에서 누락 없이 표시됨
     *   - 단순히 startDateTime만 비교하면 종료일이 다음 달로 넘어가는 모임이 빠질 수 있음
     *
     * [기존 메서드와의 차이]
     *
     * findByStudyInAndEndDateTimeAfterOrderByStartDateTimeAsc():
     *   → "현재 시각 이후" 종료되는 모임만 조회 (사이드바용, 미래 모임 전체)
     *   → 상한이 없어서 먼 미래의 모임도 모두 포함
     *
     * 이 메서드:
     *   → "특정 날짜 범위"에 겹치는 모임만 조회 (캘린더용, 월/주 단위)
     *   → 상한과 하한 모두 있어서 화면에 보이는 기간의 모임만 정확히 조회
     *   → 과거 모임도 조회 가능 (지난 달 캘린더를 볼 때)
     *
     * @param studies  조회 대상 스터디 목록 (관리중 + 참여중)
     * @param start    조회 시작 시각 (FullCalendar의 뷰 시작 경계)
     * @param end      조회 종료 시각 (FullCalendar의 뷰 끝 경계)
     * @return 날짜 범위에 겹치는 모임 목록 (시작일 오름차순)
     */
    List<Event> findByStudyInAndStartDateTimeBeforeAndEndDateTimeAfterOrderByStartDateTimeAsc(List<Study> studies, LocalDateTime end, LocalDateTime start);
}