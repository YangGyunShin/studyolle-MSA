package com.studyolle.modules.calendar;

import lombok.Builder;
import lombok.Data;

/**
 * FullCalendar.js 이벤트 데이터 전송 객체
 *
 * [FullCalendar Event Object 스펙]
 *
 * FullCalendar.js는 이벤트 소스(JSON 피드)로부터 아래 필드를 기대한다:
 *   - id:    이벤트 고유 ID (문자열 권장)
 *   - title: 캘린더에 표시되는 이벤트 제목
 *   - start: ISO 8601 형식 시작 일시 (예: "2025-03-15T14:00:00")
 *   - end:   ISO 8601 형식 종료 일시
 *   - url:   클릭 시 이동할 URL (선택, 설정하면 자동으로 <a> 태그 감싸짐)
 *   - color: 이벤트 배경색 (선택, CSS 색상값)
 *
 * [색상 구분 전략]
 *
 * 대시보드에서 사용자의 역할에 따라 색상을 달리하여 직관적 구분 제공:
 *   - 관리중 스터디 모임: #f59e0b (앰버/주황) - 관리자 아이콘과 통일
 *   - 참여중 스터디 모임: #6366f1 (인디고/파랑) - 멤버 기본색과 통일
 *   - 참가 확정 모임:     #10b981 (에메랄드/초록) - 사이드바 체크 아이콘과 통일
 *
 * 색상 우선순위: 참가 확정(초록) > 관리중(주황) > 참여중(파랑)
 * → 참가 확정이면 관리/참여 여부와 관계없이 초록으로 표시
 *
 * @see <a href="https://fullcalendar.io/docs/event-object">FullCalendar Event Object</a>
 */
@Data
@Builder
public class CalendarEventResponse {

    private String id;
    private String title;
    private String start;
    private String end;
    private String url;
    private String color;

    /** 스터디 이름 (툴팁 표시용) */
    private String studyTitle;
}