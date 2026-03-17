package com.studyolle.study.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.Set;

// /internal/studies/dashboard 응답 DTO.
// frontend-service 의 DashboardDto 와 구조 동일.
//
// studyEventsMap, enrolledEventIds 는 event-service 가 구현된 뒤 채울 예정이다.
// 현재(Phase 3)는 빈 컬렉션을 반환한다.
@Getter
@Builder
public class DashboardResponse {

    private List<StudySummaryResponse> studyManagerOf;  // 관리 중인 스터디
    private List<StudySummaryResponse> studyMemberOf;   // 참여 중인 스터디
    private List<StudySummaryResponse> studyList;       // 추천 스터디 (태그/지역 기반)

    // [Phase 5 TODO] event-service 구현 후 채울 필드
    // Map<studyId, List<EventSummaryResponse>> 구조.
    // 지금은 빈 Map 으로 반환한다.
    private Map<Long, List<Object>> studyEventsMap;     // Object 는 Phase 5 에서 EventSummaryResponse 로 교체

    // [Phase 5 TODO] event-service 에서 현재 사용자가 참가 확정한 모임 ID 목록
    private Set<Long> enrolledEventIds;
}