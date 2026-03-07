package com.studyolle.modules.calendar;

import com.studyolle.modules.account.entity.Account;
import com.studyolle.modules.account.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 캘린더 뷰 데이터 API 컨트롤러
 * <p>
 * [역할]
 * FullCalendar.js의 JSON Feed 이벤트 소스로 동작하여,
 * 로그인한 사용자의 스터디 모임 일정을 캘린더에 표시하기 위한 JSON 데이터를 제공한다.
 * <p>
 * [FullCalendar JSON Feed 연동 방식]
 * <p>
 * FullCalendar.js를 JSON Feed로 설정하면, 뷰가 전환될 때마다 자동으로
 * GET 요청을 보내어 해당 기간의 이벤트를 가져온다:
 * <p>
 * events: '/api/calendar/events'
 * <p>
 * → 월간 뷰로 전환: GET /api/calendar/events?start=2025-03-01T00:00:00&end=2025-04-01T00:00:00
 * → 주간 뷰로 전환: GET /api/calendar/events?start=2025-03-10T00:00:00&end=2025-03-17T00:00:00
 * <p>
 * start/end 파라미터는 FullCalendar가 자동으로 추가해주므로
 * 프론트엔드에서 별도로 관리할 필요가 없다.
 * <p>
 * [Repository 직접 사용]
 * <p>
 * MainController와 동일하게 Service를 거치지 않고 Repository를 직접 사용한다.
 * 단순 조회 + DTO 변환만 수행하며, 비즈니스 로직이나 상태 변경이 없기 때문이다.
 * <p>
 * [인증 요구]
 *
 * @CurrentUser Account가 null이면 빈 리스트를 반환한다.
 * SecurityConfig에서 /api/** 경로에 대한 인증 설정이 필요할 수 있으나,
 * null 체크로 방어적으로 처리한다.
 */
@RestController
@RequiredArgsConstructor
public class CalendarController {

    private final CalendarService calendarService;

    /**
     * 캘린더 이벤트 JSON 피드 엔드포인트
     *
     * @param account 현재 로그인한 사용자 (비로그인 시 null)
     * @param start   FullCalendar가 전달하는 뷰 시작 시각
     * @param end     FullCalendar가 전달하는 뷰 종료 시각
     * @return FullCalendar Event Object 배열 (JSON)
     */
    @GetMapping("/api/calendar/events")
    private List<CalendarEventResponse> calendarEvents(
            @CurrentUser Account account,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        if (account == null) {
            return List.of();
        }

        return calendarService.getCalendarEvents(account, start, end);
    }
}