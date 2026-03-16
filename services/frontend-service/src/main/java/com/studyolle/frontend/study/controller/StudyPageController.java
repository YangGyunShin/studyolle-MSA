package com.studyolle.frontend.study.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyolle.frontend.account.client.AccountInternalClient;
import com.studyolle.frontend.study.client.StudyInternalClient;
import com.studyolle.frontend.study.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 스터디 관련 모든 페이지의 라우팅을 담당하는 컨트롤러.
 *
 * [역할]
 * 1. 인증 검사 — X-Account-Id 가 없으면 /login 리다이렉트
 * 2. 권한 검사 — 관리자 페이지에 비관리자가 접근하면 /study/{path} 리다이렉트
 * 3. 데이터 조회 — StudyInternalClient 로 study-service 내부 API 호출
 * 4. 모델 적재 — Thymeleaf 에 필요한 attribute 를 model 에 담기
 * 5. 뷰 반환 — 적절한 템플릿 이름 반환
 *
 * [이 컨트롤러가 하지 않는 일]
 * - JWT 검증: api-gateway 가 이미 완료. @RequestHeader 로 결과만 받는다.
 * - 비즈니스 로직: 버튼 클릭(가입, 설정 변경 등)은 브라우저 JS 가 직접 처리.
 *   이 컨트롤러는 오직 초기 HTML 렌더링을 위한 데이터 조회만 한다.
 *
 * ====================================================================
 * [전체 요청 처리 흐름 — studyView() 기준]
 * ====================================================================
 *
 * STEP 1. 브라우저 → DispatcherServlet → StudyPageController
 * --------------------------------------------------------------------
 * 브라우저가 GET /study/my-study 를 전송한다.
 * 이 요청은 api-gateway(:8080) 를 경유해 frontend-service(:8090) 에 도달하고
 * Spring MVC 의 DispatcherServlet 이 URL 패턴을 보고
 * StudyPageController.studyView() 메서드를 호출한다.
 *
 * api-gateway 의 JwtAuthenticationFilter 가 이미 JWT 를 검증했으므로
 * X-Account-Id: 123 헤더가 붙어 있다.
 * @RequestHeader 로 꺼낸 accountId = 123L 이 메서드 파라미터로 들어온다.
 *
 * STEP 2. StudyPageController → StudyInternalClient.getStudyPageData()
 * --------------------------------------------------------------------
 * studyInternalClient.getStudyPageData("my-study", 123L) 를 호출한다.
 * 이 시점부터 제어권은 StudyInternalClient 로 넘어간다.
 *
 * STEP 3. StudyInternalClient → @LoadBalanced RestTemplate
 * --------------------------------------------------------------------
 * StudyInternalClient 가 URL 을 조립한다.
 *   "lb://STUDY-SERVICE/internal/studies/my-study/page-data?accountId=123"
 *
 * InternalHeaderHelper.build(123L) 로 HttpEntity 를 생성한다.
 *   헤더: { X-Internal-Service: frontend-service, X-Account-Id: 123 }
 *
 * RestTemplateConfig 에서 @LoadBalanced 로 등록된 RestTemplate 에
 * exchange(url, GET, httpEntity, StudyPageDataDto.class) 를 호출한다.
 *
 * STEP 4. @LoadBalanced RestTemplate → Spring Cloud LoadBalancer → Eureka
 * --------------------------------------------------------------------
 * @LoadBalanced 가 붙은 RestTemplate 은 "lb://" 로 시작하는 URL 을
 * 그대로 HTTP 로 보내지 않는다.
 * Spring Cloud LoadBalancer 가 요청을 가로채서
 * Eureka Server(:8761) 에 "STUDY-SERVICE 의 실제 IP:PORT 가 뭐야?" 를 질의한다.
 *
 * Eureka 가 "10.0.0.5:8083" 을 응답하면
 * URL 이 다음과 같이 변환된다.
 *   "http://10.0.0.5:8083/internal/studies/my-study/page-data?accountId=123"
 *
 * STEP 5. HTTP 요청 → study-service InternalRequestFilter
 * --------------------------------------------------------------------
 * 변환된 URL 로 실제 HTTP GET 요청이 전송된다.
 * study-service 의 InternalRequestFilter 가 요청을 가로챈다.
 *
 *   "/internal/**" 경로 감지
 *   X-Internal-Service 헤더 확인
 *     - 헤더 없음  → 403 Forbidden 반환 (요청 차단, 컨트롤러까지 도달하지 않음)
 *     - "frontend-service" 존재 → 통과
 *
 * STEP 6. study-service InternalStudyController 처리
 * --------------------------------------------------------------------
 * InternalStudyController.getStudyPageData() 메서드가 실행된다.
 *
 *   1) StudyRepository.findByPath("my-study") 로 Study 엔티티 조회
 *   2) accountId(123) 기반 권한 플래그 계산
 *        managers 컬렉션에 id=123 존재 → isManager = true
 *        members  컬렉션에 id=123 존재 → isMember = false
 *        JoinRequest 테이블에 PENDING 상태의 123 신청 존재 여부 → hasPendingRequest
 *   3) StudyPageDataDto 조립 후 반환
 *        { id, path, title, ..., managers:[...], members:[...],
 *          manager:true, member:false, hasPendingRequest:false }
 *
 * STEP 7. HTTP 응답 JSON → Jackson → StudyPageDataDto
 * --------------------------------------------------------------------
 * study-service 가 응답 바디를 JSON 으로 직렬화해서 반환한다.
 * RestTemplate 내부의 MappingJackson2HttpMessageConverter 가
 * JSON 문자열을 StudyPageDataDto 인스턴스로 역직렬화한다.
 * response.getBody() 로 꺼낸 StudyPageDataDto 가 StudyInternalClient 로 반환된다.
 *
 * STEP 8. StudyInternalClient → StudyPageController
 * --------------------------------------------------------------------
 * getStudyPageData() 가 StudyPageDataDto 를 반환한다.
 * 제어권이 다시 StudyPageController.studyView() 로 돌아온다.
 *
 * STEP 9. StudyPageController → Model 적재
 * --------------------------------------------------------------------
 * addCommonAttributes(model, accountId, study) 를 호출한다.
 *   model["study"]     = StudyPageDataDto 인스턴스
 *   model["isManager"] = study.isManager()  // true
 *   model["isMember"]  = study.isMember()   // false
 *   model["account"]   = AccountInternalClient.getAccountSummary(123L) 결과
 *   model["apiBase"]   = "http://localhost:8080"
 *
 * STEP 10. Thymeleaf → HTML 렌더링 → 브라우저
 * --------------------------------------------------------------------
 * ThymeleafViewResolver 가 "study/view" 를 받아
 * templates/study/view.html 을 찾아 model 의 값으로 치환한다.
 *
 *   th:if="${isManager}"           → true  → 설정 버튼 DOM 에 포함
 *   th:text="${study.title}"        → 스터디 제목 텍스트로 치환
 *   th:each="m : ${study.managers}" → managers 목록 반복 렌더링
 *
 * 완성된 HTML 이 HTTP 응답으로 브라우저에 전달된다.
 * 이후 버튼 클릭(가입, 설정 저장 등)은 브라우저 JS 가
 * API_BASE(api-gateway) 로 fetch 를 날려 처리한다.
 * ====================================================================
 */
@Controller
@RequiredArgsConstructor
public class StudyPageController {

    private final StudyInternalClient studyInternalClient;
    private final AccountInternalClient accountInternalClient;
    private final ObjectMapper objectMapper;

    @Value("${app.api-base-url}")
    private String apiBaseUrl;

    // ------------------------------------------------------------------
    // 공통 헬퍼
    // ------------------------------------------------------------------

    /**
     * 모든 스터디 페이지에서 반복되는 공통 model attribute 를 한 번에 담는다.
     * apiBase, account, study, isManager, isMember 는 모든 스터디 페이지에 필요하다.
     */
    private void addCommonAttributes(Model model, Long accountId, StudyPageDataDto study) {
        model.addAttribute("apiBase", apiBaseUrl);
        model.addAttribute("account", accountInternalClient.getAccountSummary(accountId));
        model.addAttribute("study", study);
        model.addAttribute("isManager", study.isManager());
        model.addAttribute("isMember", study.isMember());
    }

    // ------------------------------------------------------------------
    // 스터디 생성 폼
    // GET /new-study
    // ------------------------------------------------------------------

    /**
     * 스터디 생성 폼 페이지.
     * 폼 데이터는 빈 상태로 내려준다. 실제 저장은 JS fetch(POST /api/studies) 로 처리.
     */
    @GetMapping("/new-study")
    public String newStudyForm(
            @RequestHeader(value = "X-Account-Id", required = false) Long accountId,
            Model model, HttpServletResponse response) throws IOException {

        if (accountId == null) {
            response.sendRedirect("/login"); return null;
        }

        model.addAttribute("apiBase", apiBaseUrl);
        model.addAttribute("account", accountInternalClient.getAccountSummary(accountId));
        return "study/form";
    }

    // ------------------------------------------------------------------
    // 스터디 상세 뷰
    // GET /study/{path}
    // ------------------------------------------------------------------

    /**
     * 스터디 소개 페이지.
     * 비로그인도 접근 가능 (공개 스터디 열람).
     * accountId 가 있으면 study-service 가 isManager/isMember/hasPendingRequest 를 계산해 준다.
     */
    @GetMapping("/study/{path}")
    public String studyView(
            @PathVariable String path,
            @RequestHeader(value = "X-Account-Id", required = false) Long accountId,
            Model model,
            HttpServletResponse response) throws IOException {

        StudyPageDataDto study = studyInternalClient.getStudyPageData(path, accountId);
        if (study == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }

        addCommonAttributes(model, accountId, study);
        model.addAttribute("hasPendingRequest", study.isHasPendingRequest());

        // 모임 목록: event-service 연동 전까지 빈 리스트로 초기화
        model.addAttribute("newEvents", List.of());
        model.addAttribute("oldEvents", List.of());
        return "study/view";
    }

    // ------------------------------------------------------------------
    // 멤버 목록
    // GET /study/{path}/members
    // ------------------------------------------------------------------

    @GetMapping("/study/{path}/members")
    public String studyMembers(
            @PathVariable String path,
            @RequestHeader(value = "X-Account-Id", required = false) Long accountId,
            Model model,
            HttpServletResponse response) throws IOException {

        StudyPageDataDto study = studyInternalClient.getStudyPageData(path, accountId);
        if (study == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }

        addCommonAttributes(model, accountId, study);
        return "study/members";
    }

    // ------------------------------------------------------------------
    // 설정 — 소개 수정
    // GET /study/{path}/settings/description
    // ------------------------------------------------------------------

    @GetMapping("/study/{path}/settings/description")
    public String settingsDescription(
            @PathVariable String path,
            @RequestHeader(value = "X-Account-Id", required = false) Long accountId,
            Model model,
            HttpServletResponse response) throws IOException {

        if (accountId == null) {
            response.sendRedirect("/login");
            return null;
        }

        StudyPageDataDto study = studyInternalClient.getStudyPageData(path, accountId);
        if (study == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }
        if (!study.isManager()) {
            response.sendRedirect("/study/" + path);
            return null;
        }

        addCommonAttributes(model, accountId, study);
        return "study/settings/description";
    }

    // ------------------------------------------------------------------
    // 설정 — 배너
    // GET /study/{path}/settings/banner
    // ------------------------------------------------------------------

    @GetMapping("/study/{path}/settings/banner")
    public String settingsBanner(
            @PathVariable String path,
            @RequestHeader(value = "X-Account-Id", required = false) Long accountId,
            Model model,
            HttpServletResponse response) throws IOException {

        if (accountId == null) {
            response.sendRedirect("/login");
            return null;
        }

        StudyPageDataDto study = studyInternalClient.getStudyPageData(path, accountId);
        if (study == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }
        if (!study.isManager()) {
            response.sendRedirect("/study/" + path);
            return null;
        }

        addCommonAttributes(model, accountId, study);
        return "study/settings/banner";
    }

    // ------------------------------------------------------------------
    // 설정 — 태그
    // GET /study/{path}/settings/tags
    // ------------------------------------------------------------------

    /**
     * 태그 설정 페이지.
     *
     * [Tagify 초기화 데이터 JSON 직렬화]
     * Thymeleaf [[${tags}]] 로 List 를 그냥 출력하면 자바 배열 표현식이 되어
     * JS 파싱이 실패한다. ObjectMapper 로 명시적 JSON 직렬화를 해야 안전하다.
     *
     * Tagify 가 인식하는 형식: [{value:"Java"}, {value:"Spring"}]
     */
    @GetMapping("/study/{path}/settings/tags")
    public String settingsTags(
            @PathVariable String path,
            @RequestHeader(value = "X-Account-Id", required = false) Long accountId,
            Model model,
            HttpServletResponse response) throws IOException {

        if (accountId == null) {
            response.sendRedirect("/login");
            return null;
        }

        StudyPageDataDto study = studyInternalClient.getStudyPageData(path, accountId);
        if (study == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }
        if (!study.isManager()) {
            response.sendRedirect("/study/" + path);
            return null;
        }

        List<String> currentTags = studyInternalClient.getStudyTags(path, accountId);
        List<String> whitelist   = studyInternalClient.getTagWhitelist(accountId);

        addCommonAttributes(model, accountId, study);
        try {
            List<Map<String, String>> tagifyFormat = currentTags.stream()
                    .map(t -> Map.of("value", t))
                    .collect(Collectors.toList());
            model.addAttribute("tags", objectMapper.writeValueAsString(tagifyFormat));
            model.addAttribute("whitelist", objectMapper.writeValueAsString(whitelist));
        } catch (JsonProcessingException e) {
            model.addAttribute("tags", "[]");
            model.addAttribute("whitelist", "[]");
        }
        return "study/settings/tags";
    }

    // ------------------------------------------------------------------
    // 설정 — 지역
    // GET /study/{path}/settings/zones
    // ------------------------------------------------------------------

    @GetMapping("/study/{path}/settings/zones")
    public String settingsZones(
            @PathVariable String path,
            @RequestHeader(value = "X-Account-Id", required = false) Long accountId,
            Model model,
            HttpServletResponse response) throws IOException {

        if (accountId == null) {
            response.sendRedirect("/login");
            return null;
        }

        StudyPageDataDto study = studyInternalClient.getStudyPageData(path, accountId);
        if (study == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }
        if (!study.isManager()) {
            response.sendRedirect("/study/" + path);
            return null;
        }

        List<String> currentZones = studyInternalClient.getStudyZones(path, accountId);
        List<String> whitelist    = studyInternalClient.getZoneWhitelist(accountId);

        addCommonAttributes(model, accountId, study);
        try {
            List<Map<String, String>> tagifyFormat = currentZones.stream()
                    .map(z -> Map.of("value", z))
                    .collect(Collectors.toList());
            model.addAttribute("zones", objectMapper.writeValueAsString(tagifyFormat));
            model.addAttribute("whitelist", objectMapper.writeValueAsString(whitelist));
        } catch (JsonProcessingException e) {
            model.addAttribute("zones", "[]");
            model.addAttribute("whitelist", "[]");
        }
        return "study/settings/zones";
    }

    // ------------------------------------------------------------------
    // 설정 — 스터디 관리 (공개/종료/경로/이름/삭제)
    // GET /study/{path}/settings/study
    // ------------------------------------------------------------------

    @GetMapping("/study/{path}/settings/study")
    public String settingsStudy(
            @PathVariable String path,
            @RequestHeader(value = "X-Account-Id", required = false) Long accountId,
            Model model,
            HttpServletResponse response) throws IOException {

        if (accountId == null) {
            response.sendRedirect("/login");
            return null;
        }

        StudyPageDataDto study = studyInternalClient.getStudyPageData(path, accountId);
        if (study == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }
        if (!study.isManager()) {
            response.sendRedirect("/study/" + path);
            return null;
        }

        addCommonAttributes(model, accountId, study);
        return "study/settings/study";
    }

    // ------------------------------------------------------------------
    // 설정 — 가입 관리
    // GET /study/{path}/settings/join-requests
    // ------------------------------------------------------------------

    @GetMapping("/study/{path}/settings/join-requests")
    public String settingsJoinRequests(
            @PathVariable String path,
            @RequestHeader(value = "X-Account-Id", required = false) Long accountId,
            Model model,
            HttpServletResponse response) throws IOException {

        if (accountId == null) {
            response.sendRedirect("/login");
            return null;
        }

        StudyPageDataDto study = studyInternalClient.getStudyPageData(path, accountId);
        if (study == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }
        if (!study.isManager()) {
            response.sendRedirect("/study/" + path);
            return null;
        }

        List<JoinRequestDto> pendingRequests = studyInternalClient.getJoinRequests(path, accountId);

        addCommonAttributes(model, accountId, study);
        model.addAttribute("pendingRequests", pendingRequests);
        model.addAttribute("pendingCount", pendingRequests.size());
        return "study/settings/join-requests";
    }
}