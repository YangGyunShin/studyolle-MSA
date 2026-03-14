package com.studyolle.frontend.study.client;

import com.studyolle.frontend.study.dto.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

/**
 * frontend-service -> study-service 내부 API 호출을 담당하는 클라이언트.
 *
 * 모든 요청에 X-Internal-Service: frontend-service 헤더를 첨부한다.
 * study-service 의 InternalRequestFilter 가 이 헤더를 보고 내부 서비스임을 확인한다.
 *
 * lb://STUDY-SERVICE 는 @LoadBalanced RestTemplate 이 Eureka 를 통해
 * 실제 study-service 인스턴스 주소로 변환한다.
 */
@Component
public class StudyInternalClient {

    private final RestTemplate restTemplate;

    @Value("${app.study-service-base-url:lb://STUDY-SERVICE}")
    private String studyServiceBaseUrl;

    public StudyInternalClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // ------------------------------------------------------------------
    // 공통 헤더 생성 헬퍼
    // ------------------------------------------------------------------

    /**
     * 내부 서비스 호출용 공통 헤더를 생성한다.
     * X-Internal-Service 헤더가 없으면 study-service 의 InternalRequestFilter 가 403 반환.
     *
     * @param accountId 현재 로그인 사용자 ID. null 이면 비로그인 상태.
     */
    private HttpHeaders internalHeaders(Long accountId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Service", "frontend-service");
        if (accountId != null) {
            // study-service 가 권한 플래그(isManager, isMember) 계산에 사용
            headers.set("X-Account-Id", String.valueOf(accountId));
        }
        return headers;
    }

    // ------------------------------------------------------------------
    // 스터디 페이지 데이터
    // ------------------------------------------------------------------

    /**
     * 스터디 뷰/설정 페이지에 필요한 집계 데이터를 한 번에 가져온다.
     *
     * study-service: GET /internal/studies/{path}/page-data?accountId={id}
     *
     * @param path      스터디 경로
     * @param accountId 현재 사용자 ID (null 허용 — 비로그인 시 isManager=false 등)
     * @return StudyPageDataDto, 스터디가 없으면 null
     */
    public StudyPageDataDto getStudyPageData(String path, Long accountId) {
        String url = studyServiceBaseUrl + "/internal/studies/" + path + "/page-data"
                + (accountId != null ? "?accountId=" + accountId : "");
        try {
            HttpEntity<Void> entity = new HttpEntity<>(internalHeaders(accountId));
            ResponseEntity<StudyPageDataDto> response =
                    restTemplate.exchange(url, HttpMethod.GET, entity, StudyPageDataDto.class);
            return response.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            // 스터디가 존재하지 않음
            return null;
        }
    }

    // ------------------------------------------------------------------
    // 설정 페이지 전용 데이터
    // ------------------------------------------------------------------

    /**
     * 태그 설정 페이지용 현재 태그 목록과 전체 whitelist 를 가져온다.
     *
     * study-service: GET /internal/studies/{path}/tags-str
     *
     * @return 현재 등록된 태그 이름 목록 (예: ["Java", "Spring"])
     */
    public List<String> getStudyTags(String path, Long accountId) {
        String url = studyServiceBaseUrl + "/internal/studies/" + path + "/tags-str";
        try {
            HttpEntity<Void> entity = new HttpEntity<>(internalHeaders(accountId));
            ResponseEntity<List<String>> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity,
                    new ParameterizedTypeReference<List<String>>() {}
            );
            return response.getBody() != null ? response.getBody() : Collections.emptyList();
        } catch (HttpClientErrorException e) {
            return Collections.emptyList();
        }
    }

    /** 전체 태그 whitelist (Tagify 자동완성용) */
    public List<String> getTagWhitelist(Long accountId) {
        String url = studyServiceBaseUrl + "/internal/studies/tag-whitelist";
        try {
            HttpEntity<Void> entity = new HttpEntity<>(internalHeaders(accountId));
            ResponseEntity<List<String>> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity,
                    new ParameterizedTypeReference<List<String>>() {}
            );
            return response.getBody() != null ? response.getBody() : Collections.emptyList();
        } catch (HttpClientErrorException e) {
            return Collections.emptyList();
        }
    }

    /**
     * 지역 설정 페이지용 현재 지역 목록
     *
     * study-service: GET /internal/studies/{path}/zones-str
     */
    public List<String> getStudyZones(String path, Long accountId) {
        String url = studyServiceBaseUrl + "/internal/studies/" + path + "/zones-str";
        try {
            HttpEntity<Void> entity = new HttpEntity<>(internalHeaders(accountId));
            ResponseEntity<List<String>> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity,
                    new ParameterizedTypeReference<List<String>>() {}
            );
            return response.getBody() != null ? response.getBody() : Collections.emptyList();
        } catch (HttpClientErrorException e) {
            return Collections.emptyList();
        }
    }

    /** 전체 지역 whitelist */
    public List<String> getZoneWhitelist(Long accountId) {
        String url = studyServiceBaseUrl + "/internal/studies/zone-whitelist";
        try {
            HttpEntity<Void> entity = new HttpEntity<>(internalHeaders(accountId));
            ResponseEntity<List<String>> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity,
                    new ParameterizedTypeReference<List<String>>() {}
            );
            return response.getBody() != null ? response.getBody() : Collections.emptyList();
        } catch (HttpClientErrorException e) {
            return Collections.emptyList();
        }
    }

    /**
     * 가입 신청 목록 (승인제 전용)
     *
     * study-service: GET /internal/studies/{path}/join-requests
     */
    public List<JoinRequestDto> getJoinRequests(String path, Long accountId) {
        String url = studyServiceBaseUrl + "/internal/studies/" + path + "/join-requests";
        try {
            HttpEntity<Void> entity = new HttpEntity<>(internalHeaders(accountId));
            ResponseEntity<List<JoinRequestDto>> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity,
                    new ParameterizedTypeReference<List<JoinRequestDto>>() {}
            );
            return response.getBody() != null ? response.getBody() : Collections.emptyList();
        } catch (HttpClientErrorException e) {
            return Collections.emptyList();
        }
    }

    // ------------------------------------------------------------------
    // 대시보드 데이터
    // ------------------------------------------------------------------

    /**
     * 대시보드에 필요한 모든 데이터 (내 스터디 + 캘린더 + 추천)
     *
     * study-service: GET /internal/studies/dashboard?accountId={id}
     */
    public DashboardDto getDashboard(Long accountId) {
        String url = studyServiceBaseUrl + "/internal/studies/dashboard?accountId=" + accountId;
        try {
            HttpEntity<Void> entity = new HttpEntity<>(internalHeaders(accountId));
            ResponseEntity<DashboardDto> response =
                    restTemplate.exchange(url, HttpMethod.GET, entity, DashboardDto.class);
            return response.getBody();
        } catch (HttpClientErrorException e) {
            return new DashboardDto(); // 빈 대시보드
        }
    }

    /**
     * 최근 공개된 스터디 목록 (비로그인 랜딩 페이지용)
     *
     * study-service: GET /internal/studies/recent
     */
    public List<StudySummaryDto> getRecentStudies() {
        String url = studyServiceBaseUrl + "/internal/studies/recent";
        try {
            HttpEntity<Void> entity = new HttpEntity<>(internalHeaders(null));
            ResponseEntity<List<StudySummaryDto>> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity,
                    new ParameterizedTypeReference<List<StudySummaryDto>>() {}
            );
            return response.getBody() != null ? response.getBody() : Collections.emptyList();
        } catch (HttpClientErrorException e) {
            return Collections.emptyList();
        }
    }
}