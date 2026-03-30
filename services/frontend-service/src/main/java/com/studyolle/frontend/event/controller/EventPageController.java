package com.studyolle.frontend.event.controller;

import com.studyolle.frontend.account.client.AccountInternalClient;
import com.studyolle.frontend.account.dto.AccountSummaryDto;
import com.studyolle.frontend.event.client.EventInternalClient;
import com.studyolle.frontend.event.dto.EnrollmentDto;
import com.studyolle.frontend.event.dto.EventSummaryDto;
import com.studyolle.frontend.study.client.StudyInternalClient;
import com.studyolle.frontend.study.dto.StudyPageDataDto;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * 모임(Event) 관련 HTML 페이지를 서빙하는 컨트롤러.
 *
 * [URL 구조]
 *   GET /study/{path}/events/new          - 모임 생성 폼 (관리자 전용)
 *   GET /study/{path}/events/{eventId}/edit - 모임 수정 폼 (관리자 전용)
 *   GET /study/{path}/events/{eventId}    - 모임 상세 보기
 *
 * [Spring MVC 경로 우선순위]
 *   /events/new 는 리터럴이므로 /events/{eventId} 보다 높은 우선순위를 가진다.
 *   "new" 를 Long 으로 파싱할 수 없으므로 eventView 핸들러로는 라우팅되지 않는다.
 *
 * [인증]
 *   api-gateway OptionalJwtFilter 가 쿠키의 accessToken 을 검증하고
 *   X-Account-Id 헤더를 추가한다. 토큰이 없으면 헤더도 없다(비로그인).
 */
@Controller
@RequiredArgsConstructor
public class EventPageController {

    private final StudyInternalClient studyInternalClient;
    private final EventInternalClient eventInternalClient;
    private final AccountInternalClient accountInternalClient;

    @Value("${app.api-base-url:http://localhost:8080}")
    private String apiBase;

    // ------------------------------------------------------------------
    // 모임 생성 폼
    // ------------------------------------------------------------------

    @GetMapping("/study/{path}/events/new")
    public String newEventForm(
            @PathVariable String path,
            @RequestHeader(value = "X-Account-Id", required = false) Long accountId,
            Model model,
            HttpServletResponse response) throws IOException {

        StudyPageDataDto study = studyInternalClient.getStudyPageData(path, accountId);

        if (study == null) {
            response.sendError(404);
            return null;
        }
        if (!study.isManager()) {
            response.sendError(403);
            return null;
        }

        AccountSummaryDto account = accountInternalClient.getAccountSummary(accountId);

        model.addAttribute("study", study);
        model.addAttribute("account", account);
        model.addAttribute("isManager", true);
        model.addAttribute("isMember", study.isMember());
        model.addAttribute("hasPendingRequest", study.isHasPendingRequest());
        model.addAttribute("event", null);  // 신규 생성이므로 null
        model.addAttribute("isEdit", false);
        model.addAttribute("apiBase", apiBase);
        return "event/form";
    }

    // ------------------------------------------------------------------
    // 모임 수정 폼
    // ------------------------------------------------------------------

    @GetMapping("/study/{path}/events/{eventId}/edit")
    public String editEventForm(
            @PathVariable String path,
            @PathVariable Long eventId,
            @RequestHeader(value = "X-Account-Id", required = false) Long accountId,
            Model model,
            HttpServletResponse response) throws IOException {

        StudyPageDataDto study = studyInternalClient.getStudyPageData(path, eventId);

        if (study == null) {
            response.sendError(404);
            return null;
        }
        if (!study.isManager()) {
            response.sendError(403);
            return null;
        }

        EventSummaryDto event = eventInternalClient.getEventById(eventId);
        if (event == null) {
            response.sendError(404);
            return null;
        }

        AccountSummaryDto account = accountInternalClient.getAccountSummary(accountId);

        model.addAttribute("study", study);
        model.addAttribute("account", account);
        model.addAttribute("isManager", true);
        model.addAttribute("isMember", study.isMember());
        model.addAttribute("hasPendingRequest", study.isHasPendingRequest());
        model.addAttribute("event", event);
        model.addAttribute("isEdit", true);
        model.addAttribute("apiBase", apiBase);
        return "event/form";
    }

    // ------------------------------------------------------------------
    // 모임 상세 보기
    // ------------------------------------------------------------------

    @GetMapping("/study/{path}/events/{eventId}")
    public String eventView(
            @PathVariable String path,
            @PathVariable Long eventId,
            @RequestHeader(value = "X-Account-Id", required = false) Long accountId,
            Model model,
            HttpServletResponse response) throws IOException {

        StudyPageDataDto study = studyInternalClient.getStudyPageData(path, eventId);
        if (study == null) {
            response.sendError(404);
            return null;
        }

        EventSummaryDto event = eventInternalClient.getEventById(eventId);
        if (event == null) {
            response.sendError(404);
            return null;
        }

        AccountSummaryDto account = accountInternalClient.getAccountSummary(accountId);
        boolean isManager = study.isManager();
        boolean isMember = study.isMember();

        // 내 enrollment 탐색
        // event-service 가 반환하는 event.enrollments 는 이 모임에 신청한 모든 사람의 목록이다.
        // view.html 에서 현재 로그인한 사용자의 상태에 따라 버튼이 달라지기 때문에
        // 전체 목록 중 "내 것"만 꺼내야 한다.
        //
        //   신청 안 함  (myEnrollment == null)           → "참가 신청하기" 버튼
        //   신청했고 대기 중 (accepted == false)          → "승인 대기 중" 표시
        //   신청했고 수락됨  (accepted == true)           → "참가 확정" + "참가 취소" 버튼
        //   출석까지 완료   (attended == true)            → "출석 완료" 표시
        //
        // stream().filter() 로 accountId 가 일치하는 항목만 골라내고,
        // findFirst() 로 딱 하나만 꺼낸다. (중복 신청은 불가하므로 항상 0개 or 1개)
        // orElse(null) 은 신청 내역이 없는 상태(= 미신청)를 null 로 표현한다.
        //
        // 별도 API (ex. GET /events/{id}/my-enrollment) 를 만들지 않는 이유:
        // 어차피 관리자의 수락/거절/출석체크를 위해 enrollments 전체가 필요하므로
        // 이미 받아온 데이터에서 Java stream 으로 걸러내는 것이 추가 네트워크 호출 없이 효율적이다.
        EnrollmentDto myEnrollment = null;
        if (accountId != null && event.getEnrollments() != null) {
            myEnrollment = event.getEnrollments().stream()
                    .filter(e -> accountId.equals(e.getAccountId()))
                    .findFirst()
                    .orElse(null);
        }

        LocalDateTime now = LocalDateTime.now();

        // 신청 가능: 로그인 + 비관리자 + 미신청 + 신청 마감 전 + 모임 종료 전
        boolean isEnrollable = accountId != null
                && !isManager
                && myEnrollment == null
                && event.getEndEnrollmentDateTime() != null
                && event.getEndEnrollmentDateTime().isAfter(now)
                && event.getEndDateTime() != null
                && event.getEndDateTime().isAfter(now);

        // 취소 가능: 신청했고 + 모임 시작 전
        boolean isDisenrollable = myEnrollment != null
                && event.getStartDateTime() != null
                && event.getStartDateTime().isAfter(now);

        model.addAttribute("study", study);
        model.addAttribute("account", account);
        model.addAttribute("event", event);
        model.addAttribute("isManager", isManager);
        model.addAttribute("isMember", isMember);
        model.addAttribute("hasPendingRequest", study.isHasPendingRequest());
        model.addAttribute("myEnrollment", myEnrollment);
        model.addAttribute("isEnrollable", isEnrollable);
        model.addAttribute("isDisenrollable", isDisenrollable);
        model.addAttribute("apiBase", apiBase);
        return "event/view";
    }
}