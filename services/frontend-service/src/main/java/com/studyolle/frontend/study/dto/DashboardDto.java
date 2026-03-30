package com.studyolle.frontend.study.dto;

import com.studyolle.frontend.event.dto.EventSummaryDto;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 대시보드(index.html 로그인 상태) 렌더링에 필요한 집계 DTO.
 *
 * study-service 의 GET /internal/studies/dashboard?accountId={id} 가 반환한다.
 */
@Data
public class DashboardDto {

    /** 관리중인 스터디 목록 */
    private List<StudySummaryDto> studyManagerOf;

    /** 참여중인 스터디 목록 */
    private List<StudySummaryDto> studyMemberOf;

    /**
     * 스터디 ID -> 예정 모임 목록 매핑.
     * 관리중/참여중 스터디에 각각의 예정 모임을 붙여서 대시보드 카드로 표시한다.
     */
    private Map<Long, List<EventSummaryDto>> studyEventsMap;

    /** 현재 사용자가 참가 확정한 모임 ID 집합 */
    private Set<Long> enrolledEventIds;

    /** 추천 스터디 목록 (태그/지역 기반) */
    private List<StudySummaryDto> studyList;
}