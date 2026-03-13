package com.studyolle.modules.main;

import com.studyolle.modules.account.entity.Account;
import com.studyolle.modules.account.repository.AccountRepository;
import com.studyolle.modules.event.entity.Event;
import com.studyolle.modules.event.repository.EnrollmentRepository;
import com.studyolle.modules.event.repository.EventRepository;
import com.studyolle.modules.study.repository.StudyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 애플리케이션의 메인 진입점을 담당하는 컨트롤러
 *
 * =====================================================
 * [이 클래스의 역할]
 *
 * 사용자가 가장 먼저 접하는 페이지들을 담당:
 *   1. 홈 화면 (/)         -> 로그인 여부에 따라 다른 뷰 반환
 *   2. 로그인 페이지 (/login) -> Spring Security의 커스텀 로그인 폼
 *   3. 스터디 검색 (/search/study) -> 키워드 기반 스터디 검색 + 페이징
 *
 * =====================================================
 * [패키지 위치와 설계 의도]
 *
 * modules.main 패키지에는 특정 도메인에 종속되지 않는 공통 컴포넌트가 위치:
 *   - MainController (이 클래스)  -> 메인 페이지, 로그인, 검색
 *   - ExceptionAdvice            -> 전역 예외 처리
 *
 * 홈 화면은 account, study, event, enrollment 등 여러 도메인의 데이터를
 * 조합하여 보여주므로 특정 도메인 모듈에 넣기 어려움
 * -> main 패키지가 이런 "교차 도메인" 기능의 적절한 위치
 *
 * =====================================================
 * [SecurityConfig과의 관계]
 *
 * SecurityConfig.java에서 다음 URL들이 permitAll()로 설정되어 있음:
 *   "/", "/login", "/search/study"
 *
 * 즉, 이 컨트롤러의 모든 엔드포인트는 비인증 사용자도 접근 가능
 * 다만, 홈 화면("/")에서는 로그인 여부에 따라 다른 콘텐츠를 보여줌
 * -> Account 파라미터가 null이면 비로그인, 아니면 로그인 상태
 *
 * =====================================================
 * [의존 Repository 구성]
 *
 * 이 컨트롤러는 Service 계층을 거치지 않고 Repository를 직접 사용하고 있음
 * -> 홈 화면/검색은 단순 조회(read-only)이므로 별도 비즈니스 로직이 필요 없음
 * -> 트랜잭션 관리나 도메인 로직이 필요 없는 순수 조회 작업
 *
 * 만약 조회 로직이 복잡해지거나 캐싱 등이 필요해지면
 * Service 계층을 도입하는 것이 바람직함
 */
@Controller
@RequiredArgsConstructor
public class MainController {

    private final StudyRepository studyRepository;           // 스터디 조회용 Repository
    private final AccountRepository accountRepository;       // 계정 정보(태그/지역 포함) 조회용 Repository
    private final EnrollmentRepository enrollmentRepository; // 참가 확정 여부 판별용 Repository
    private final EventRepository eventRepository;           // 예정 모임 조회용 Repository

    /**
     * 홈 화면 - 로그인 여부에 따라 다른 뷰를 반환
     *
     * =====================================================
     * [로그인 상태 분기 처리]
     *
     * Account 파라미터로 현재 로그인 상태를 판별:
     *   - account != null -> 로그인 상태 -> "index-after-login" 뷰
     *   - account == null -> 비로그인 상태 -> "index" 뷰
     *
     * Spring Security + @CurrentUser(또는 HandlerMethodArgumentResolver)를 통해
     * 현재 인증된 사용자의 Account 객체가 자동 주입됨
     * -> 비로그인 시에는 null이 들어옴
     *
     * =====================================================
     * [로그인 사용자에게 보여주는 데이터 (index-after-login)]
     *
     *   1. accountLoaded: 태그/지역 정보까지 함께 로딩된 Account 객체
     *      -> findAccountWithTagsAndZonesById(): JOIN FETCH로 N+1 문제 방지
     *      -> 파라미터로 받은 account는 태그/지역이 LAZY 상태일 수 있음
     *
     *   2. studyList: 관심 태그/지역 기반 추천 스터디
     *      -> 사용자가 설정한 관심 태그 + 지역과 매칭되는 스터디 조회
     *      -> 개인화된 스터디 추천 기능
     *
     *   3. studyManagerOf: 내가 운영 중인 스터디 (최대 5개)
     *      -> managers에 현재 사용자 포함 + closed=false (진행 중)
     *      -> publishedDateTime 기준 내림차순 (최근 공개 순)
     *
     *   4. studyMemberOf: 내가 참여 중인 스터디 (최대 5개)
     *      -> members에 현재 사용자 포함 + closed=false (진행 중)
     *      -> publishedDateTime 기준 내림차순
     *
     *   5. studyEventsMap: 스터디별 예정 모임 Map (Map<Long, List<Event>>)
     *      -> 관리중 + 참여중 스터디를 IN 절로 한 번에 조회 (N+1 방지)
     *      -> Study.id를 key로 그룹핑, 사이드바에서 스터디 하위에 모임 표시
     *
     *   6. enrolledEventIds: 내가 참가 확정한 Event ID Set (Set<Long>)
     *      -> 사이드바에서 참가 확정 모임(✅)과 미참가 모임(📅) 시각적 구분용
     *      -> accepted=true인 Enrollment에서 Event ID만 추출
     *
     * =====================================================
     * [비로그인 사용자에게 보여주는 데이터 (index)]
     *
     *   studyList: 최근 공개된 스터디 9개
     *     -> published=true, closed=false (공개 + 진행 중)
     *     -> publishedDateTime 기준 내림차순 (최신 공개 순)
     *     -> 비로그인 사용자에게도 서비스의 활성도를 보여주기 위한 목적
     *
     * @param account 현재 로그인한 사용자 (비로그인 시 null)
     * @param model   View에 전달할 데이터
     * @return 로그인 시 "index-after-login", 비로그인 시 "index"
     */
    @GetMapping("/")
    public String home(Account account, Model model) {
        if (account != null) {
            // 로그인 상태: 태그/지역을 포함한 Account를 별도로 로딩
            // -> 파라미터의 account는 SecurityContext에서 가져온 것으로
            //    LAZY 연관관계(tags, zones)가 로딩되지 않았을 수 있음
            // -> JOIN FETCH 쿼리를 사용하는 전용 메서드로 다시 조회
            Account accountLoaded = accountRepository.findAccountWithTagsAndZonesById(account.getId());

            if (accountLoaded == null) {
                return "index";  // 조회 실패 시 비로그인 화면으로 fallback
            }

            model.addAttribute(accountLoaded);

            // ✅ 프로필 완성도 계산 (5개 항목, 각 20%)
            int completion = 0;
            if (accountLoaded.isEmailVerified()) completion += 20;
            if (StringUtils.hasText(accountLoaded.getBio())) completion += 20;
            if (StringUtils.hasText(accountLoaded.getProfileImage())) completion += 20;
            if (accountLoaded.getTags().size() > 0) completion += 20;
            if (accountLoaded.getZones().size() > 0) completion += 20;
            model.addAttribute("profileCompletion", completion);

            // 관심 태그/지역 기반 추천 스터디
            model.addAttribute("studyList",
                    studyRepository.findByAccount(accountLoaded.getTags(), accountLoaded.getZones()));

            // 내가 운영 중인 스터디 (최대 5개, 최근 공개 순)
            List<Study> studyManagerOf = studyRepository
                    .findFirst5ByManagersContainingAndClosedOrderByPublishedDateTimeDesc(account, false);
            model.addAttribute("studyManagerOf", studyManagerOf);

            // 내가 참여 중인 스터디 (최대 5개, 최근 공개 순)
            List<Study> studyMemberOf = studyRepository
                    .findFirst5ByMembersContainingAndClosedOrderByPublishedDateTimeDesc(account, false);
            model.addAttribute("studyMemberOf", studyMemberOf);

            // 스터디별 예정 모임 Map (사이드바에서 스터디 하위에 모임 표시용)
            // -> 관리중 + 참여중 스터디를 합쳐 IN 절로 한 번에 조회 (N+1 방지)
            // -> Study.id를 key로 그룹핑하여 템플릿에서 O(1) 조회
            List<Study> allMyStudies = new ArrayList<>();
            allMyStudies.addAll(studyManagerOf);
            allMyStudies.addAll(studyMemberOf);

            Map<Long, List<Event>> studyEventsMap = Map.of();
            if (!allMyStudies.isEmpty()) {
                List<Event> upcomingEvents = eventRepository
                        .findByStudyInAndEndDateTimeAfterOrderByStartDateTimeAsc(allMyStudies, LocalDateTime.now());
                studyEventsMap = upcomingEvents.stream()
                        .collect(Collectors.groupingBy(event -> event.getStudy().getId()));
            }
            model.addAttribute("studyEventsMap", studyEventsMap);

            // 내가 참가 확정한 Event ID Set (사이드바에서 ✅/📅 아이콘 구분용)
            // -> accepted=true인 Enrollment에서 Event ID만 추출
            Set<Long> enrolledEventIds = enrollmentRepository
                    .findByAccountAndAcceptedOrderByEnrolledAtDesc(accountLoaded, true)
                    .stream()
                    .map(e -> e.getEvent().getId())
                    .collect(Collectors.toSet());
            model.addAttribute("enrolledEventIds", enrolledEventIds);

            return "index-after-login";
        }

        // 비로그인 상태: 최근 공개된 스터디 9개만 표시
        model.addAttribute("studyList",
                studyRepository.findFirst9ByPublishedAndClosedOrderByPublishedDateTimeDesc(true, false));
        return "index";
    }

    /**
     * 로그인 페이지
     *
     * Spring Security의 커스텀 로그인 폼 페이지를 반환
     * -> SecurityConfig.java에서 .loginPage("/login")으로 이 경로를 지정하고 있음
     * -> 이 설정이 없으면 Spring Security가 제공하는 기본 로그인 페이지가 표시됨
     *
     * 로그인 처리 자체(POST /login)는 Spring Security가 자동으로 처리하므로
     * 이 컨트롤러에는 GET 매핑만 존재
     * -> Spring Security의 UsernamePasswordAuthenticationFilter가
     *    POST /login 요청을 가로채서 인증 처리를 수행
     *
     * @return "login" -> templates/login.html 뷰를 렌더링
     */
    @GetMapping("/login")
    public String login() {
        return "login";
    }

    /**
     * 스터디 키워드 검색 (페이징 + 정렬 지원)
     *
     * =====================================================
     * [@PageableDefault의 역할]
     *
     * Spring Data의 Pageable 파라미터에 기본값을 지정:
     *   - size = 9      : 한 페이지당 9개 스터디 표시
     *   - page = 0      : 첫 번째 페이지부터 시작 (0-indexed)
     *   - sort           : publishedDateTime 기준 정렬
     *   - direction      : DESC (최신 공개 순)
     *
     * 클라이언트에서 ?page=2&size=10&sort=memberCount,desc 등으로
     * 쿼리 파라미터를 보내면 기본값 대신 해당 값이 사용됨
     *
     * =====================================================
     * [검색 동작 흐름]
     *
     * 1. 사용자가 검색어(keyword)를 입력하여 GET /search/study?keyword=... 요청
     * 2. StudyRepository.findByKeyword()가 키워드 기반 검색 수행
     *    -> QueryDSL 또는 JPQL의 LIKE 검색, fulltext 검색 등으로 구현
     * 3. 결과는 Page<Study> 객체로 반환 (페이징 메타데이터 포함)
     * 4. 뷰(search.html)에서 결과 목록 + 페이지네이션 UI를 렌더링
     *
     * =====================================================
     * [뷰에 전달하는 데이터]
     *
     *   studyPage: Page<Study> 객체
     *     -> content: 현재 페이지의 스터디 목록
     *     -> totalElements: 전체 검색 결과 수
     *     -> totalPages: 전체 페이지 수
     *     -> number: 현재 페이지 번호
     *     -> hasPrevious(), hasNext() 등 페이지네이션 헬퍼 메서드 포함
     *
     *   keyword: 현재 검색어
     *     -> 검색 결과 메시지 표시, 페이지네이션 링크에 검색어 유지 등에 사용
     *     -> mark.js 라이브러리가 이 값을 기반으로 검색어 하이라이트 처리
     *
     *   sortProperty: 현재 정렬 기준
     *     -> publishedDateTime(공개일) 또는 memberCount(멤버 수)
     *     -> 정렬 드롭다운 UI에서 활성 항목 표시 및 페이지네이션 링크 생성에 사용
     *
     * @param pageable @PageableDefault로 기본값이 설정된 페이징 정보
     * @param keyword  검색 키워드 (쿼리 파라미터)
     * @param model    View에 전달할 데이터
     * @param sort     정렬 정보 (Spring이 자동 바인딩)
     * @return "search" -> templates/search.html 뷰를 렌더링
     */
    @GetMapping("/search/study")
    public String searchStudy(@PageableDefault(size = 9, page = 0, sort = "publishedDateTime",
                                      direction = Sort.Direction.DESC) Pageable pageable,
                              String keyword, Model model, Sort sort,
                              @RequestParam(required = false) boolean recruiting,
                              @RequestParam(required = false) boolean open) {

        // 키워드로 스터디 검색 (Pageable에 포함된 페이징/정렬 조건 적용)
        Page<Study> studyPage = studyRepository.findByKeyword(keyword, pageable, recruiting, open);

        model.addAttribute("studyPage", studyPage);    // 검색 결과 (페이지 객체)
        model.addAttribute("keyword", keyword);         // 현재 검색어 (하이라이트 + 링크 유지용)
        model.addAttribute("recruiting", recruiting);
        model.addAttribute("open", open);

        // 현재 정렬 기준 판별
        // -> pageable.getSort().toString()은 "publishedDateTime: DESC" 같은 형태
        // -> publishedDateTime이 포함되어 있으면 "publishedDateTime", 아니면 "memberCount"
        // -> search.html의 정렬 드롭다운에서 현재 활성화된 정렬 기준을 표시하는 데 사용
        model.addAttribute("sortProperty",
                pageable.getSort().toString().contains("publishedDateTime")
                        ? "publishedDateTime" : "memberCount");

        return "search";
    }

    /**
     * 이메일 인증 필요 안내 페이지
     * - EmailVerificationInterceptor에서 리다이렉트되는 목적지
     */
    @GetMapping("/email-verification-required")
    public String emailVerificationRequired(Account account, Model model) {
        model.addAttribute("account", account);
        return "account/email-verification-required";
    }
}