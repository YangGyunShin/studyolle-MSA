package com.studyolle.study.controller;

import com.studyolle.study.client.MetadataFeignClient;
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
 * StudyController — 스터디 핵심 CRUD + 가입/탈퇴 + 검색
 *
 * =============================================
 * [모노리틱 참조: StudyController.java]
 * =============================================
 *
 * [핵심 변경: @Controller → @RestController]
 * 모노리틱에서는 Thymeleaf 뷰를 반환했으나,
 * MSA 에서는 JSON 을 반환하고 frontend-service 가 뷰 렌더링을 담당한다.
 *
 * [핵심 변경: @CurrentUser Account → @RequestHeader("X-Account-Id") Long]
 * 모노리틱에서는 @CurrentUser 어노테이션 + HandlerMethodArgumentResolver 로
 * SecurityContext 에서 Account 를 꺼냈으나,
 * MSA 에서는 api-gateway 가 JWT 를 검증한 뒤 X-Account-Id 헤더를 주입한다.
 *
 * [핵심 변경: EventRepository 제거]
 * 모노리틱에서는 StudyController 가 EventRepository 를 직접 주입받아
 * 스터디 상세 페이지에서 모임 목록을 함께 보여줬으나,
 * MSA 에서는 event-service 가 독립적으로 관리하므로 Feign Client 로 대체 예정.
 * (event-service Phase 추가 후 EventFeignClient 구현)
 *
 * =============================================
 * 헤더 X-Account-Nickname 추가 이유
 * =============================================
 * createJoinRequest 에서 JoinRequest.accountNickname 비정규화 필드에 저장하기 위해
 * 닉네임도 헤더에서 읽는다. api-gateway 가 JWT claims 에서 추출해 주입한다.
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
     * POST /api/studies
     *
     * [모노리틱 참조: StudyController.newStudySubmit(account, form)]
     * path 중복 검증은 StudyService.createNewStudy() 내부에서 수행.
     */
    @PostMapping
    public ResponseEntity<CommonApiResponse<StudyResponse>> createStudy(
            @RequestHeader("X-Account-Id") Long accountId,
            @Valid @RequestBody CreateStudyRequest request) {

        Study study = studyService.createNewStudy(request, accountId);
        return ResponseEntity.ok(CommonApiResponse.ok(StudyResponse.from(study)));
    }

    // ============================
    // 스터디 조회
    // ============================

    /**
     * GET /api/studies/{path}
     *
     * [모노리틱 참조: StudyController.viewStudy(account, path)]
     */
    @GetMapping("/{path}")
    public ResponseEntity<CommonApiResponse<StudyResponse>> viewStudy(
            @PathVariable String path) {

        Study study = studyService.getStudy(path);
        return ResponseEntity.ok(CommonApiResponse.ok(StudyResponse.from(study)));
    }

    // ============================
    // 스터디 멤버 가입/탈퇴
    // ============================

    /**
     * POST /api/studies/{path}/join
     *
     * [모노리틱 참조: StudyController.joinStudy(account, path)]
     * joinType 에 따라 즉시가입 / 승인제 신청 분기.
     *
     * OPEN → study.addMember(): 즉시 memberIds 에 추가
     * APPROVAL_REQUIRED → joinRequest 생성 (PENDING)
     */
    @PostMapping("/{path}/join")
    public ResponseEntity<CommonApiResponse<Void>> joinStudy(
            @RequestHeader("X-Account-Id") Long accountId,
            @RequestHeader("X-Account-Nickname") String nickname,
            @PathVariable String path) {

        Study study = studyService.getStudy(path);
        if (!study.isJoinable(accountId)) {
            throw new IllegalStateException("가입할 수 없는 스터디입니다.");
        }

        if (study.isApprovalRequired()) {
            // 승인제: JoinRequest 생성
            studyService.createJoinRequest(study, accountId, nickname);
            return ResponseEntity.ok(CommonApiResponse.ok("가입 신청이 접수되었습니다. 관리자 승인을 기다려주세요."));
        } else {
            // 자유 가입: 즉시 멤버 등록
            studyService.addMember(study, accountId);
            return ResponseEntity.ok(CommonApiResponse.ok("스터디에 가입되었습니다."));
        }
    }

    /**
     * DELETE /api/studies/{path}/leave
     *
     * [모노리틱 참조: StudyController.leaveStudy(account, path)]
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
     * [모노리틱 참조: SearchController.search(keyword, model, pageable)]
     * [2단계 검색 흐름]
     * 1. MetadataFeignClient 로 keyword 매칭 tagIds, zoneIds 조회
     * 2. StudyService.searchStudies() 로 QueryDSL 검색 수행
     *
     * @param keyword   검색어 (제목, 태그, 지역 매칭)
     * @param recruiting true = 모집 중인 스터디만
     * @param open      true = 종료되지 않은 스터디만
     */
    @GetMapping("/search")
    public ResponseEntity<CommonApiResponse<Page<StudySummaryResponse>>> searchStudies(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "false") boolean recruiting,
            @RequestParam(defaultValue = "true") boolean open,
            @PageableDefault(size = 9, sort = "publishedDateTime") Pageable pageable) {

        // 1단계: keyword 에 매칭되는 tagIds, zoneIds 조회
        Set<Long> tagIds = metadataFeignClient.findTagIdsByKeyword(keyword, "study-service");
        Set<Long> zoneIds = metadataFeignClient.findZoneIdsByKeyword(keyword, "study-service");

        // 2단계: QueryDSL 검색
        Page<Study> studyPage = studyService.searchStudies(
                keyword, tagIds, zoneIds, pageable, recruiting, open);

        Page<StudySummaryResponse> result = studyPage.map(StudySummaryResponse::from);
        return ResponseEntity.ok(CommonApiResponse.ok(result));
    }

    // ============================
    // 대시보드용 목록
    // ============================

    /**
     * GET /api/studies/my/managing
     *
     * 내가 관리자로 참여 중인 스터디 5개. 대시보드 좌측 패널에서 사용.
     *
     * [모노리틱 참조: MainController 에서 studyRepository.findFirst5ByManagersContaining 직접 호출]
     * MSA 에서는 Controller → Service → Repository 계층을 준수한다.
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
     * 내가 멤버로 참여 중인 스터디 5개. 대시보드 좌측 패널에서 사용.
     */
    @GetMapping("/my/joined")
    public ResponseEntity<CommonApiResponse<List<StudySummaryResponse>>> getMyJoinedStudies(
            @RequestHeader("X-Account-Id") Long accountId) {

        List<StudySummaryResponse> result = studyService.getStudiesAsMember(accountId)
                .stream().map(StudySummaryResponse::from).toList();
        return ResponseEntity.ok(CommonApiResponse.ok(result));
    }

    /**
     * GET /api/studies/recommended
     *
     * 사용자 관심사(tagIds, zoneIds) 기반 추천 스터디 9개.
     * 대시보드 우측 사이드바에서 사용.
     *
     * tagIds, zoneIds 는 account-service 가 반환한 값을 frontend-service 가 전달한다.
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