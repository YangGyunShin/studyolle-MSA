package com.studyolle.study.client;

import com.studyolle.study.client.dto.EventSummaryDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * event-service 내부 API 호출 Feign Client.
 *
 * 대시보드 렌더링 시 현재 로그인한 사용자가 신청한 모임 목록을 가져온다.
 * event-service 의 InternalRequestFilter 통과를 위해
 * 모든 요청에 X-Internal-Service: study-service 헤더를 포함한다.
 */
@FeignClient(name = "event-service")
public interface EventFeignClient {

    /**
     * 특정 사용자가 신청(enrollment)한 모임 목록을 가져온다.
     *
     * event-service: GET /internal/events/calendar?accountId={id}
     *
     * 반환된 모임은 studyPath 필드를 포함하므로,
     * 호출 측에서 studyPath → studyId 로 변환해 Map 을 조립할 수 있다.
     */
    @GetMapping("/internal/events/calendar")
    List<EventSummaryDto> getCalendarEvents(
            @RequestParam Long accountId,
            @RequestHeader("X-Internal-Service") String serviceName);

    /**
     * 특정 스터디의 모임 목록을 가져온다.
     * <p>
     * event-service: GET /internal/events/by-study/{studyPath}
     * <p>
     * 대시보드에서 "관리중인 스터디"와 "참여중인 스터디"에 예정된 모임을 보여줄 때 사용.
     * enrollment 여부와 무관하게 해당 스터디에 등록된 모든 모임을 반환한다.
     */
    @GetMapping("/internal/events/by-study/{studyPath}")
    List<EventSummaryDto> getEventsByStudy(
            @PathVariable String studyPath,
            @RequestHeader("X-Internal-Service") String serviceName
    );
}