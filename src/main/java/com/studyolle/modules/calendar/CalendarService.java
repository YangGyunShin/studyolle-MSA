package com.studyolle.modules.calendar;

import com.studyolle.modules.account.entity.Account;
import com.studyolle.modules.event.entity.Event;
import com.studyolle.modules.event.repository.EnrollmentRepository;
import com.studyolle.modules.event.repository.EventRepository;
import com.studyolle.modules.study.entity.Study;
import com.studyolle.modules.study.repository.StudyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 캘린더 뷰 데이터 조합 서비스
 *
 * [역할]
 * 여러 도메인(Study, Event, Enrollment)의 데이터를 조합하여
 * FullCalendar.js가 소비할 수 있는 DTO 목록을 생성한다.
 *
 * CalendarController와 동일한 패키지에 위치하는 이유:
 *   - 특정 도메인 모듈에 속하지 않는 교차 도메인 조회 서비스
 *   - Study, Event, Enrollment 3개 모듈의 Repository를 조합
 *   - MSA 전환 시 BFF(Backend for Frontend) 서비스로 자연스럽게 분리
 *
 * [Service 분리 원칙 - 컨트롤러 책임 기준]
 *
 * 프로젝트의 기존 패턴을 따름:
 *   - StudyController         → StudyService         (사용자 액션)
 *   - StudySettingsController → StudySettingsService (관리자 액션)
 *   - CalendarController      → CalendarService       (캘린더 데이터 조합)
 *
 * 컨트롤러가 직접 Repository를 호출하던 로직을 서비스로 추출하여
 * 계층 분리 원칙(Controller → Service → Repository)을 준수한다.
 *
 * [트랜잭션 설정]
 *
 * 클래스 레벨에 @Transactional(readOnly = true)을 선언하여
 * 모든 메서드에 읽기 전용 트랜잭션을 적용한다.
 *   - JPA dirty checking 비활성화 → 성능 최적화
 *   - 이 서비스는 데이터 변경이 없으므로 readOnly가 적절
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CalendarService {

    private final StudyRepository studyRepository;
    private final EventRepository eventRepository;
    private final EnrollmentRepository enrollmentRepository;

    /** 캘린더 색상 상수 (main-style.css의 C팔레트와 통일) */
    private static final String COLOR_MANAGING = "#bfa473";   // 앰버: 관리중 스터디
    private static final String COLOR_MEMBER   = "#8e85b0";   // 인디고: 참여중 스터디
    private static final String COLOR_ENROLLED = "#7fac8e";   // 에메랄드: 참가 확정

    /**
     * 로그인 사용자의 캘린더 이벤트 목록을 조합하여 반환
     * <p>
     * [처리 흐름]
     * <p>
     * 1. 관리중/참여중 스터디 목록 조회 (2번의 쿼리)
     * 2. 관리중 스터디 ID Set 생성 (색상 결정 + 중복 제거용, 메모리 O(1) 판별)
     * 3. 두 목록을 합치되 중복 제거 (관리자이면서 멤버인 경우)
     * 4. 날짜 범위에 해당하는 모임 조회 (1번의 IN 쿼리)
     * 5. 참가 확정 Event ID Set 조회 (1번의 쿼리)
     * 6. DTO 변환 + 색상 결정
     * <p>
     * → 총 4번의 쿼리로 모든 데이터 조회 (N+1 없음)
     * <p>
     * [색상 결정 우선순위]
     * <p>
     * 하나의 모임에 여러 역할이 겹칠 수 있다: 예) 관리자이면서 참가 확정된 모임
     * <p>
     * 우선순위: 참가 확정(초록) > 관리중(주황) > 참여중(파랑)
     * → 사용자에게 가장 중요한 정보(내가 참가하는 모임)를 우선 강조
     *
     * @param account 현재 로그인한 사용자
     * @param start   FullCalendar 뷰의 시작 시각
     * @param end     FullCalendar 뷰의 종료 시각
     * @return FullCalendar Event Object DTO 목록
     */
    public List<CalendarEventResponse> getCalendarEvents(Account account, LocalDateTime start, LocalDateTime end) {
        // 1. 관리중/참여중 스터디 조회
        List<Study> managingStudies = studyRepository.findFirst5ByManagersContainingAndClosedOrderByPublishedDateTimeDesc(account, false);
        List<Study> memberStudies = studyRepository.findFirst5ByMembersContainingAndClosedOrderByPublishedDateTimeDesc(account, false);

        // 2. 관리중 스터디 ID Set (색상 결정용)
        Set<Long> managingStudyIds = managingStudies.stream()
                .map(Study::getId)
                .collect(Collectors.toSet());

        // 3. 전체 스터디 목록 (중복 제거)
        List<Study> allStudies = new ArrayList<>(managingStudies);
        memberStudies.stream()
                .filter(s -> !managingStudyIds.contains(s.getId()))
                .forEach(allStudies::add);

        if (allStudies.isEmpty()) {
            return List.of();
        }

        // 4. 날짜 범위에 해당하는 모임 조회 (1번의 IN 쿼리)
        List<Event> events = eventRepository.findByStudyInAndStartDateTimeBeforeAndEndDateTimeAfterOrderByStartDateTimeAsc(allStudies, end, start);

        // 5. 참가 확정 Event ID Set
        Set<Long> enrolledEventIds = enrollmentRepository.findByAccountAndAcceptedOrderByEnrolledAtDesc(account, true)
                .stream()
                .map(e -> e.getEvent().getId())
                .collect(Collectors.toSet());

        // 6. DTO 변환 (색상 우선순위: 참가확정 > 관리중 > 참여중)
        return events.stream()
                .map(event -> CalendarEventResponse.builder()
                        .id(String.valueOf(event.getId()))
                        .title(event.getTitle())
                        .start(event.getStartDateTime().toString())
                        .end(event.getEndDateTime().toString())
                        .url("/study/" + event.getStudy().getEncodedPath() + "/events/" + event.getId())
                        .color(determineColor(event.getId(), event.getStudy().getId(), enrolledEventIds, managingStudyIds))
                        .studyTitle(event.getStudy().getTitle())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 캘린더 이벤트 색상 결정
     *
     * 우선순위: 참가 확정(초록) > 관리중(주황) > 참여중(파랑)
     *
     * @param eventId          이벤트 ID
     * @param studyId          해당 이벤트의 스터디 ID
     * @param enrolledEventIds 사용자가 참가 확정한 Event ID Set
     * @param managingStudyIds 사용자가 관리중인 Study ID Set
     * @return CSS 색상값
     */
    private String determineColor(Long eventId, Long studyId, Set<Long> enrolledEventIds, Set<Long> managingStudyIds) {

        if (enrolledEventIds.contains(eventId)) {
            return COLOR_ENROLLED;
        }
        if (managingStudyIds.contains(studyId)) {
            return COLOR_MANAGING;
        }
        return COLOR_MEMBER;
    }
}