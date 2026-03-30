package com.studyolle.frontend.event.client;

import com.studyolle.frontend.common.InternalHeaderHelper;
import com.studyolle.frontend.event.dto.EventSummaryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

/**
 * frontend-service -> event-service 내부 API 호출 클라이언트.
 *
 * 모든 요청에 X-Internal-Service: frontend-service 헤더를 첨부한다.
 * event-service 의 InternalRequestFilter 가 이 헤더로 내부 서비스를 식별한다.
 *
 * lb://EVENT-SERVICE 는 @LoadBalanced RestTemplate 이 Eureka 를 통해
 * 실제 event-service 인스턴스 주소로 변환한다.
 */
@Component
@RequiredArgsConstructor
public class EventInternalClient {

    private final RestTemplate restTemplate;

    @Value("${app.event-service-base-url:lb://EVENT-SERVICE}")
    private String eventServiceBaseUrl;

    /**
     * 특정 스터디의 모임 목록 전체를 가져온다.
     * 호출 측에서 endDateTime 기준으로 예정/지난 모임으로 분리한다.
     *
     * event-service: GET /internal/events/by-study/{studyPath}
     */
    public List<EventSummaryDto> getEventsByStudy(String studyPath) {
        String url = eventServiceBaseUrl + "/internal/events/by-study/" + studyPath;
        try {
            ResponseEntity<List<EventSummaryDto>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    InternalHeaderHelper.build(null),
                    new ParameterizedTypeReference<List<EventSummaryDto>>() {}
            );
            return response.getBody() != null ? response.getBody() : Collections.emptyList();
        } catch (HttpClientErrorException e) {
            return Collections.emptyList();
        }
    }

    /**
     * 단일 모임 상세 정보 (enrollment 목록 포함).
     * event/view.html 과 event/form.html(수정 폼) 에서 사용.
     *
     * event-service: GET /internal/events/{eventId}
     *
     * @return 모임이 없으면 null
     */
    public EventSummaryDto getEventById(Long eventId) {
        String url = eventServiceBaseUrl + "/internal/events/" + eventId;
        try {
            ResponseEntity<EventSummaryDto> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    InternalHeaderHelper.build(null),
                    EventSummaryDto.class
            );
            return response.getBody();
        } catch (HttpClientErrorException e) {
            return null;
        }
    }
}