package com.studyolle.frontend;

import com.studyolle.frontend.account.client.AccountInternalClient;
import com.studyolle.frontend.account.dto.AccountSummaryDto;
import com.studyolle.frontend.study.client.StudyInternalClient;
import com.studyolle.frontend.study.dto.DashboardDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * 메인 홈 페이지 컨트롤러.
 *
 * GET / -> templates/index.html
 *
 * index.html 은 account 가 null 인지에 따라 두 화면으로 분기한다.
 *   - null:      비로그인 랜딩 (히어로 + 기능 소개 + 최근 스터디)
 *   - not null:  로그인 대시보드 (내 스터디 + 캘린더 + 추천 스터디)
 *
 * [왜 이 컨트롤러가 account/ 나 study/ 패키지 안에 없는가]
 * account-service(AccountInternalClient)와 study-service(StudyInternalClient)
 * 양쪽 모두에서 데이터를 가져오기 때문에 특정 도메인 패키지에 귀속시키기 어렵다.
 * 여러 도메인을 조합하는 컨트롤러는 최상위 controller/ 패키지에 둔다.
 */
@Controller
@RequiredArgsConstructor
public class HomeController {

    private final StudyInternalClient studyInternalClient;
    private final AccountInternalClient accountInternalClient;

    @Value("${app.api-base-url}")
    private String apiBaseUrl;

    @GetMapping("/")
    public String home(
            // api-gateway 가 JWT 를 검증한 뒤 추가하는 헤더.
            // 비로그인 요청은 이 헤더가 없으므로 required = false.
            @RequestHeader(value = "X-Account-Id", required = false) Long accountId,
            Model model) {

        model.addAttribute("apiBase", apiBaseUrl);

        if (accountId == null) {
            // 비로그인: 최근 공개 스터디만 보여주고 랜딩 화면 반환
            model.addAttribute("account", null);
            model.addAttribute("studyList", studyInternalClient.getRecentStudies());
            return "index";
        }

        // 로그인 상태 — 대시보드 구성

        // 1. 계정 정보 (nav 바 닉네임 + 프로필 완성도 계산용)
        AccountSummaryDto account = accountInternalClient.getAccountSummary(accountId);
        model.addAttribute("account", account);

        // 2. 대시보드 집계 (내 스터디 + 예정 모임 + 추천 스터디)
        //    study-service 의 /internal/studies/dashboard 가 한 번에 모아서 반환한다.
        DashboardDto dashboard = studyInternalClient.getDashboard(accountId);
        model.addAttribute("studyManagerOf",   dashboard.getStudyManagerOf());
        model.addAttribute("studyMemberOf",    dashboard.getStudyMemberOf());
        model.addAttribute("studyEventsMap",   dashboard.getStudyEventsMap());
        model.addAttribute("enrolledEventIds", dashboard.getEnrolledEventIds());
        model.addAttribute("studyList",        dashboard.getStudyList());

        // 3. 프로필 완성도 계산 (0~100)
        //    모노리틱에서는 MainService.calculateProfileCompletion() 이 담당했던 로직.
        //    MSA 에서는 AccountSummaryDto 의 필드를 보고 frontend-service 가 직접 계산한다.
        //    기준: 이메일 인증(25) + 소개(25) + 관심 태그(25) + 활동 지역(25)
        int completion = 0;
        if (account != null) {
            if (account.isEmailVerified()) {
                completion += 25;
            }

            if (account.getBio() != null && !account.getBio().isBlank()) {
                completion += 25;
            }

            if (account.getTagCount() > 0) {
                completion += 25;
            }

            if (account.getZoneCount() > 0){
                completion += 25;
            }
        }
        model.addAttribute("profileCompletion", completion);

        return "index";
    }
}