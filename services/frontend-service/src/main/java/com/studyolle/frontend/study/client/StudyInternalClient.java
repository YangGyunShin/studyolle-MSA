package com.studyolle.frontend.study.client;

import com.studyolle.frontend.common.InternalHeaderHelper;
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

        // [STEP 3-a] URL 조립
        // studyServiceBaseUrl = "lb://STUDY-SERVICE" (@Value 주입)
        // 최종 조립 결과: "lb://STUDY-SERVICE/internal/studies/my-study/page-data?accountId=123"
        // "lb://" 접두사는 @LoadBalanced RestTemplate 이 처리하는 특수 스킴이다.
        // 일반 HTTP 클라이언트로는 이 URL 로 요청할 수 없다.
        String url = studyServiceBaseUrl + "/internal/studies/" + path + "/page-data"
                + (accountId != null ? "?accountId=" + accountId : "");
        try {
            // [STEP 3-b] InternalHeaderHelper 로 공통 헤더가 담긴 HttpEntity 생성
            // HttpEntity 구조: 바디 없음(Void) + 헤더 두 개
            //   X-Internal-Service: frontend-service  <- study-service InternalRequestFilter 통과용
            //   X-Account-Id: 123                     <- study-service 가 isManager/isMember 계산에 사용
            //
            // [STEP 3-c] @LoadBalanced RestTemplate.exchange() 호출
            // 이 시점에 Spring Cloud LoadBalancer 가 개입한다.
            //   "lb://STUDY-SERVICE" → Eureka Server(:8761) 질의 → 실제 IP:PORT 획득
            //   → "http://10.0.0.5:8083/internal/studies/my-study/page-data?accountId=123" 으로 변환 후 전송
            //
            // [STEP 7] 응답 수신 및 역직렬화
            // study-service 가 반환한 JSON 을
            // MappingJackson2HttpMessageConverter 가 StudyPageDataDto 로 역직렬화한다.
            // StudyPageDataDto.class 가 Jackson 에게 "이 타입으로 변환해" 라고 알려주는 힌트다.
            ResponseEntity<StudyPageDataDto> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    InternalHeaderHelper.build(accountId),  // STEP 3-b
                    StudyPageDataDto.class                  // STEP 7: 역직렬화 타입 힌트
            );

            // [STEP 8] StudyPageDataDto 를 StudyPageController 로 반환
            return response.getBody();

        } catch (HttpClientErrorException.NotFound e) {
            // study-service 가 404 를 반환한 경우 (스터디 경로가 존재하지 않음).
            // StudyPageController 가 null 을 받으면 response.sendError(404) 로 처리한다.
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
            ResponseEntity<List<String>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    InternalHeaderHelper.build(accountId),
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
            ResponseEntity<List<String>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    InternalHeaderHelper.build(accountId),
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
            ResponseEntity<List<String>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    InternalHeaderHelper.build(accountId),
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
            ResponseEntity<List<String>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    InternalHeaderHelper.build(accountId),
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
            ResponseEntity<List<JoinRequestDto>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    InternalHeaderHelper.build(accountId),
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
            ResponseEntity<DashboardDto> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    InternalHeaderHelper.build(accountId),
                    DashboardDto.class
            );
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
            ResponseEntity<List<StudySummaryDto>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    InternalHeaderHelper.build(null),
                    new ParameterizedTypeReference<List<StudySummaryDto>>() {}
            );
            return response.getBody() != null ? response.getBody() : Collections.emptyList();
        } catch (HttpClientErrorException e) {
            return Collections.emptyList();
        }
    }
}

/*
 * ====================================================================
 * [설계 해설] StudyInternalClient 는 왜 이렇게 생겼는가
 * ====================================================================
 *
 * 1. RestTemplate 을 직접 쓰는 이유
 * --------------------------------------------------------------------
 * Spring 에서 서비스 간 HTTP 통신에는 세 가지 선택지가 있다.
 *
 *   (a) RestTemplate   — 동기(Synchronous). 응답이 올 때까지 스레드가 블로킹됨.
 *   (b) WebClient      — 비동기(Reactive). Spring WebFlux 와 잘 맞음.
 *   (c) OpenFeign      — 인터페이스 선언만으로 HTTP 클라이언트를 자동 생성.
 *
 * frontend-service 는 Spring MVC(서블릿 기반) 로 구성되어 있다.
 * WebClient 는 Reactive 스택에서 진가를 발휘하므로 MVC 와 함께 쓰면
 * 오히려 코드가 복잡해진다 (.block() 호출이 남발되는 등).
 * OpenFeign 은 편리하지만 인터페이스-구현체 분리가 생겨 오히려
 * 코드 추적이 어려워질 수 있다.
 * RestTemplate 은 "요청을 보내고 응답을 받는다"는 흐름이
 * 코드에 명확히 드러나기 때문에 학습 목적과 소규모 프로젝트에 적합하다.
 *
 * 2. @LoadBalanced RestTemplate 이어야 하는 이유
 * --------------------------------------------------------------------
 * RestTemplateConfig 에서 @LoadBalanced 를 붙인 RestTemplate 빈을 등록했다.
 * 이 애너테이션이 없으면 "lb://STUDY-SERVICE" 라는 URL 을 그대로
 * HTTP 요청으로 보내려 해서 UnknownHostException 이 발생한다.
 *
 * @LoadBalanced 가 있으면 Spring Cloud LoadBalancer 가 URL 을 가로채서
 * Eureka 레지스트리에서 STUDY-SERVICE 의 실제 주소(IP:PORT)를 조회한 뒤
 * "http://192.168.x.x:8083/internal/studies/..." 형태로 변환해 준다.
 *
 *   lb://STUDY-SERVICE/internal/studies/{path}
 *     |
 *     | Eureka 조회
 *     v
 *   http://10.0.0.5:8083/internal/studies/{path}   <- 실제 전송되는 URL
 *
 * 3. exchange() 를 쓰는 이유
 * --------------------------------------------------------------------
 * RestTemplate 은 getForObject(), postForEntity(), exchange() 등 여러
 * 메서드를 제공한다.
 *
 *   getForObject()  : 응답 바디만 반환. 헤더를 보낼 수 없다.
 *   exchange()      : 요청 메서드, URL, 헤더, 바디를 모두 직접 제어 가능.
 *
 * 모든 내부 요청에 X-Internal-Service 헤더를 반드시 포함해야 하므로
 * 헤더를 HttpEntity 로 전달할 수 있는 exchange() 를 선택했다.
 * X-Internal-Service 헤더가 없으면 study-service 의 InternalRequestFilter
 * 가 403 을 반환하도록 설계되어 있기 때문이다.
 *
 * 4. ParameterizedTypeReference 가 필요한 이유
 * --------------------------------------------------------------------
 * Java 의 제네릭은 런타임에 타입 정보가 지워진다 (Type Erasure).
 * 따라서 restTemplate.exchange(..., List<String>.class) 라고 쓸 수 없다.
 * List<String>.class 자체가 문법 오류이기 때문이다.
 *
 * ParameterizedTypeReference 는 이 문제를 우회하기 위한 Spring 의 해법이다.
 * 익명 클래스를 생성하면서 제네릭 타입 파라미터를 클래스 계층 구조에 보존해
 * 런타임에도 "List 의 원소 타입이 String 이다" 라는 정보를 Jackson 이 읽을
 * 수 있게 한다.
 *
 *   new ParameterizedTypeReference<List<String>>() {}
 *   //                                              ^^ 익명 클래스 생성이 핵심
 *
 * 5. try-catch 로 감싸는 이유
 * --------------------------------------------------------------------
 * RestTemplate 은 4xx / 5xx 응답을 받으면 예외를 던진다
 * (기본 동작: HttpClientErrorException, HttpServerErrorException).
 * study-service 가 일시적으로 내려가거나, 경로가 없거나, 권한이 없을 때
 * 예외가 frontend-service 전체로 전파되면 사용자는 500 에러를 보게 된다.
 *
 * 각 메서드에서 적절한 폴백(null, emptyList())을 반환하면,
 * 스터디가 없는 경우 404 페이지를, 데이터가 없는 경우 빈 목록을
 * 자연스럽게 보여줄 수 있다.
 * ====================================================================
 */