package com.studyolle.study.controller;

import com.studyolle.study.client.MetadataFeignClient;
import com.studyolle.study.common.EmailVerifiedGuard;
import com.studyolle.study.dto.response.CommonApiResponse;
import com.studyolle.study.dto.response.StudyResponse;
import com.studyolle.study.dto.response.StudySummaryResponse;
import com.studyolle.study.dto.request.CreateStudyRequest;
import com.studyolle.study.entity.Study;
import com.studyolle.study.service.StudyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * 스터디 핵심 CRUD, 가입/탈퇴, 검색 API 컨트롤러.
 *
 * =============================================
 * @RestController vs @Controller
 * =============================================
 *
 * 모노리틱에서는 @Controller 를 사용하여 Thymeleaf 뷰 이름을 반환했다.
 * MSA 에서는 @RestController 를 사용하여 JSON 을 반환한다.
 *
 * @RestController 는 @Controller + @ResponseBody 의 조합이다.
 * @ResponseBody 가 붙으면 메서드의 반환값이 뷰 이름이 아닌
 * HTTP 응답 바디에 직접 직렬화(JSON)된다.
 * 뷰 렌더링은 frontend-service 가 담당한다.
 *
 * =============================================
 * 사용자 식별 방식 변경
 * =============================================
 *
 * 모노리틱에서는 @CurrentUser Account account 파라미터로 로그인 사용자를 꺼냈다.
 * @CurrentUser 는 HandlerMethodArgumentResolver + SecurityContext 를 이용한 커스텀 어노테이션이었다.
 *
 * MSA 에서는 api-gateway 가 JWT 를 검증한 뒤 X-Account-Id, X-Account-Nickname 헤더를 주입한다.
 * 컨트롤러는 @RequestHeader 로 이 헤더를 꺼내기만 하면 된다.
 * Spring Security 설정이나 JWT 라이브러리가 필요 없다.
 *
 * =============================================
 * X-Account-Nickname 헤더를 사용하는 이유
 * =============================================
 *
 * 승인제(APPROVAL_REQUIRED) 가입 신청 시 JoinRequest 에 accountNickname 을 비정규화 저장한다.
 * 신청 목록을 보여줄 때 account-service 를 별도 호출하지 않아도 되도록 하기 위함이다.
 * api-gateway 는 JWT claims 의 nickname 필드에서 이 값을 추출해 헤더로 주입한다.
 */
@RestController
@RequestMapping("/api/studies")
@RequiredArgsConstructor
public class StudyController {

    private final StudyService studyService;
    private final MetadataFeignClient metadataFeignClient;

    // ============================
    // 스터디 생성
    // ============================

    /**
     * POST /api/studies — 스터디 생성
     *
     * [Phase 8 옵션 B 추가]
     * @RequestHeader("X-Account-Email-Verified") 로 이메일 인증 여부를 받아
     * EmailVerifiedGuard 로 검증한다.
     * 인증 안 된 사용자의 스터디 생성을 백엔드 단에서 직접 차단한다 (2차 방어선).
     *
     * frontend-service 의 EmailVerifiedInterceptor 가 이미 /new-study 페이지 접근을 차단하므로 정상 흐름에서는 여기까지 오지 않는다.
     * 그러나 개발자 도구로 fetch 를 직접 호출해 우회하는 경우를 차단하기 위해 백엔드 체크가 필수적이다.
     * 회원 권한 변경 기능의 방어 깊이와 동일한 설계 원칙.
     */
    @PostMapping
    public ResponseEntity<CommonApiResponse<StudyResponse>> createStudy(
            @RequestHeader("X-Account-Id") Long accountId,
            @RequestHeader("X-Account-Email-Verified") Boolean emailVerified,
            @Valid @RequestBody CreateStudyRequest request) {

        EmailVerifiedGuard.require(emailVerified);

        Study study = studyService.createNewStudy(request, accountId);
        return ResponseEntity.ok(CommonApiResponse.ok(StudyResponse.from(study)));
    }

    // ============================
    // 스터디 조회
    // ============================

    /**
     * GET /api/studies/{path}
     *
     * path 로 스터디 상세 정보를 조회한다.
     * 인증이 필요 없는 공개 조회이므로 X-Account-Id 헤더를 요구하지 않는다.
     * api-gateway 라우팅 설정에서 JwtAuthenticationFilter 를 적용하지 않으면 비로그인 접근도 허용된다.
     */
    @GetMapping("/{path}")
    public ResponseEntity<CommonApiResponse<StudyResponse>> viewStudy(
            @PathVariable String path) {

        Study study = studyService.getStudy(path);
        return ResponseEntity.ok(CommonApiResponse.ok(StudyResponse.from(study)));
    }

    // ============================
    // 멤버 가입/탈퇴
    // ============================

    /**
     * POST /api/studies/{path}/join
     *
     * 스터디 가입을 처리한다. joinType 에 따라 즉시 가입 또는 승인 신청으로 분기한다.
     *
     * OPEN            → studyService.addMember(): 즉시 memberIds 에 추가
     * APPROVAL_REQUIRED → studyService.createJoinRequest(): PENDING 상태 신청 생성
     *
     * 두 경로 모두 같은 엔드포인트를 사용하고 서버가 joinType 을 확인하여 처리한다.
     * 클라이언트는 joinType 을 알 필요 없이 항상 같은 URL 로 요청하면 된다.
     *
     * [Phase 8 옵션 B 추가]
     * 이메일 인증 안 한 사용자의 스터디 가입을 백엔드에서 차단한다.
     * 가입은 프론트 인터셉터가 막지 못하는 API 직접 호출 경로이므로 (fragments.html 의
     * joinStudy JS 함수 참고) 백엔드 가드가 실질적인 방어선이 된다.
     */
    @PostMapping("/{path}/join")
    public ResponseEntity<CommonApiResponse<Void>> joinStudy(
            @RequestHeader("X-Account-Id") Long accountId,
            @RequestHeader("X-Account-Nickname") String nickname,
            @RequestHeader("X-Account-Email-Verified") Boolean emailVerified,
            @PathVariable String path) {

        EmailVerifiedGuard.require(emailVerified);

        Study study = studyService.getStudy(path);
        if (!study.isJoinable(accountId)) {
            throw new IllegalStateException("가입할 수 없는 스터디입니다.");
        }

        if (study.isApprovalRequired()) {
            studyService.createJoinRequest(study, accountId, nickname); // 승인제: 신청 생성
            return ResponseEntity.ok(CommonApiResponse.ok("가입 신청이 접수되었습니다. 관리자 승인을 기다려주세요."));
        } else {
            studyService.addMember(study, accountId); // 자유 가입: 즉시 멤버 등록
            return ResponseEntity.ok(CommonApiResponse.ok("스터디에 가입되었습니다."));
        }
    }

    /**
     * DELETE /api/studies/{path}/leave
     *
     * 스터디에서 탈퇴한다. memberIds 에서 accountId 를 제거한다.
     */
    @DeleteMapping("/{path}/leave")
    public ResponseEntity<CommonApiResponse<Void>> leaveStudy(
            @RequestHeader("X-Account-Id") Long accountId,
            @PathVariable String path) {

        Study study = studyService.getStudy(path);
        studyService.removeMember(study, accountId);
        return ResponseEntity.ok(CommonApiResponse.ok("스터디에서 탈퇴했습니다."));
    }

    // ============================
    // 스터디 검색
    // ============================

    /**
     * GET /api/studies/search?keyword=&recruiting=&open=
     *
     * 키워드로 스터디를 검색한다. 제목, 태그, 지역을 모두 검색 대상으로 삼는다.
     *
     * 2단계 검색 흐름:
     * 1단계 — MetadataFeignClient 로 keyword 에 이름이 매칭되는 tagIds, zoneIds 를 조회한다.
     *         study-service DB 에는 ID 만 있어서 이름 검색이 불가능하기 때문이다.
     * 2단계 — 조회한 tagIds, zoneIds 와 keyword 를 StudyService.searchStudies() 에 전달한다.
     *         QueryDSL 이 title LIKE, tagIds IN, zoneIds IN 조건을 OR 로 묶어 검색한다.
     *
     * @PageableDefault: 페이지 사이즈 기본값 9, 정렬 기준 publishedDateTime.
     *                   URL 에 ?page=1&size=9&sort=publishedDateTime,desc 로 재정의 가능.
     */
    @GetMapping("/search")
    public ResponseEntity<CommonApiResponse<Page<StudySummaryResponse>>> searchStudies(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "false") boolean recruiting,
            @RequestParam(defaultValue = "true") boolean open,
            @PageableDefault(size = 9, sort = "publishedDateTime") Pageable pageable) {

        // 1단계: metadata-service 에서 keyword 와 이름이 매칭되는 ID 목록 조회
        Set<Long> tagIds = metadataFeignClient.findTagIdsByKeyword(keyword, "study-service");
        Set<Long> zoneIds = metadataFeignClient.findZoneIdsByKeyword(keyword, "study-service");

        // 2단계: QueryDSL 검색 — tagIds/zoneIds 가 빈 Set 이어도 안전하게 처리됨 (Impl 참고)
        Page<Study> studyPage = studyService.searchStudies(
                keyword, tagIds, zoneIds, pageable, recruiting, open);

        return ResponseEntity.ok(CommonApiResponse.ok(studyPage.map(StudySummaryResponse::from)));
    }

    // ============================
    // 대시보드용 목록
    // ============================

    /**
     * GET /api/studies/my/managing
     *
     * 내가 관리자로 참여 중인 활동 중인 스터디 최대 5개를 반환한다.
     * 대시보드 좌측 패널 "내가 운영 중인 스터디" 섹션에서 사용한다.
     */
    @GetMapping("/my/managing")
    public ResponseEntity<CommonApiResponse<List<StudySummaryResponse>>> getMyManagingStudies(
            @RequestHeader("X-Account-Id") Long accountId) {

        List<StudySummaryResponse> result = studyService.getStudiesAsManager(accountId)
                .stream().map(StudySummaryResponse::from).toList();
        return ResponseEntity.ok(CommonApiResponse.ok(result));
    }

    /**
     * GET /api/studies/my/joined
     *
     * 내가 멤버로 참여 중인 활동 중인 스터디 최대 5개를 반환한다.
     * 대시보드 좌측 패널 "내가 참여 중인 스터디" 섹션에서 사용한다.
     */
    @GetMapping("/my/joined")
    public ResponseEntity<CommonApiResponse<List<StudySummaryResponse>>> getMyJoinedStudies(
            @RequestHeader("X-Account-Id") Long accountId) {

        List<StudySummaryResponse> result = studyService.getStudiesAsMember(accountId)
                .stream().map(StudySummaryResponse::from).toList();
        return ResponseEntity.ok(CommonApiResponse.ok(result));
    }

    /**
     * GET /api/studies/recommended?tagIds=1,2&zoneIds=3,4
     *
     * 사용자 관심사(태그/지역 ID) 기반 추천 스터디 최대 9개를 반환한다.
     * 대시보드 우측 사이드바에서 사용한다.
     *
     * tagIds, zoneIds 는 account-service 가 반환한 사용자 관심사 정보를
     * frontend-service 가 쿼리 파라미터로 전달한다.
     * required = false: 관심사가 없는 새 회원의 경우 파라미터 없이 호출해도 된다.
     * 이 경우 StudyService 가 최신 공개 스터디 9개를 대신 반환한다.
     */
    @GetMapping("/recommended")
    public ResponseEntity<CommonApiResponse<List<StudySummaryResponse>>> getRecommendedStudies(
            @RequestParam(required = false) Set<Long> tagIds,
            @RequestParam(required = false) Set<Long> zoneIds) {

        List<StudySummaryResponse> result = studyService.getRecommendedStudies(tagIds, zoneIds)
                .stream().map(StudySummaryResponse::from).toList();
        return ResponseEntity.ok(CommonApiResponse.ok(result));
    }
}

/*
 * ============================================================
 * [컨트롤러 계층의 역할과 설계 원칙]
 * ============================================================
 *
 * 1. 컨트롤러는 얇게(thin) 유지한다
 * ------------------------------------------------------------
 * 컨트롤러의 역할은 다음 세 가지로 한정한다:
 *   1. HTTP 요청 파라미터 수신 (헤더, @PathVariable, @RequestBody 등)
 *   2. 서비스 메서드 호출
 *   3. HTTP 응답 반환
 *
 * 비즈니스 로직(가입 가능 여부 판단, 상태 검증 등)은 서비스나 엔티티로 위임해야 한다.
 * joinStudy() 에서 isJoinable() 체크를 컨트롤러가 하고 있는 것은
 * 가급적 서비스로 내려야 하는 후보이다.
 *
 *
 * 2. ResponseEntity 를 사용하는 이유
 * ------------------------------------------------------------
 * ResponseEntity<T> 는 HTTP 응답 바디(T)와 상태 코드, 헤더를 함께 제어할 수 있다.
 *
 *   ResponseEntity.ok(body)          → 200 OK + 바디
 *   ResponseEntity.notFound().build() → 404 Not Found (바디 없음)
 *   ResponseEntity.status(201).body() → 201 Created + 바디
 *
 * 단순히 객체를 반환하는 것보다 HTTP 의미를 명확히 표현할 수 있다.
 *
 *
 * 3. CommonApiResponse 래퍼를 쓰는 이유
 * ------------------------------------------------------------
 * 모든 응답을 { "status": "ok", "data": {...} } 형태로 통일하면
 * 클라이언트(frontend-service)가 일관된 방식으로 응답을 처리할 수 있다.
 * 성공/실패 여부를 HTTP 상태 코드와 응답 바디 두 군데에서 확인할 수 있어
 * 에러 처리가 용이하다.
 *
 *
 * 4. @PageableDefault 와 Pageable
 * ------------------------------------------------------------
 * Spring Data Web 이 제공하는 기능으로, URL 쿼리 파라미터를 Pageable 객체로 자동 변환한다.
 *
 * 예시:
 *   GET /api/studies/search?keyword=spring&page=0&size=9&sort=publishedDateTime,desc
 *   → Pageable { page=0, size=9, sort=[publishedDateTime: DESC] }
 *
 * @PageableDefault(size = 9, sort = "publishedDateTime") 는
 * URL 에 페이징 파라미터가 없을 때 사용할 기본값을 지정한다.
 * page=0(첫 페이지), size=9, sort=publishedDateTime(정렬 방향 미지정 시 ASC).
 */