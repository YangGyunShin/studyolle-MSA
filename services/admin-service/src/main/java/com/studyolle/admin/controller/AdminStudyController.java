package com.studyolle.admin.controller;

import com.studyolle.admin.client.StudyAdminClient;
import com.studyolle.admin.client.dto.PageResponse;
import com.studyolle.admin.client.dto.StudyAdminDto;
import com.studyolle.admin.common.InternalHeaderConstants;
import com.studyolle.admin.dto.response.CommonApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 관리자 스터디 관리 REST API.
 *
 * [경로 정책]
 * AdminMemberController 와 동일하게 /api/admin/** 아래에 둔다.
 * admin-gateway 의 JwtAuthenticationFilter + AdminRoleFilter 가 1차 검증하고, 이 서비스 내부의 AdminAuthInterceptor 가 2차 검증한다.
 * 컨트롤러 메서드 자체에는 권한 어노테이션이 없다.
 *
 * [단순 위임 구조]
 * admin-service 는 자체 DB 가 없는 orchestration 서비스다.
 * 이 컨트롤러도 거의 순수한 pass-through 이지만,
 * 강제 비공개 엔드포인트만 감사 의미로 요청자 id 를 Feign 헤더에 주입하는 역할을 한다.
 *
 * [회원 관리와 다른 점 — 자기 자신 검증이 없다]
 * 회원 권한 변경에는 "관리자가 자기 권한을 강등하면 복구 불가능" 이라는 위험이 있어
 * 자기 자신 검증을 모든 계층에서 했다.
 * 스터디 강제 종료는 "관리자가 자기 스터디를 강제 종료" 하는 행위 자체가 합법적(플랫폼 관리자는 자기 스터디라도 문제가 있다고 판단하면 닫을 수 있어야 한다) 이므로 자기 자신 검증이 없다.
 */
@RestController
@RequestMapping("/api/admin/studies")
@RequiredArgsConstructor
public class AdminStudyController {

    private final StudyAdminClient studyAdminClient;

    /*
     * GET /api/admin/studies?keyword=xxx&page=0&size=20
     *
     * 관리자 스터디 목록 조회.
     */
    @GetMapping
    public ResponseEntity<CommonApiResponse<PageResponse<StudyAdminDto>>> listStudies(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageResponse<StudyAdminDto> result = studyAdminClient.listStudies(
                keyword,
                page,
                size,
                InternalHeaderConstants.SERVICE_NAME);

        return ResponseEntity.ok(CommonApiResponse.ok(result));
    }

    /*
     * POST /api/admin/studies/{path}/force-close
     *
     * 특정 스터디를 강제 비공개 처리한다.
     *
     * [왜 본문을 받지 않는가]
     * 이 엔드포인트의 동작은 "스터디를 강제 종료" 로 명확해서 추가 파라미터가 필요 없다.
     * RESTful 관점에서 상태 전이를 의도한 POST 는 본문이 필수가 아니다.
     *
     * [왜 X-Account-Id 를 헤더로 받는가]
     * 게이트웨이의 JwtAuthenticationFilter 가 토큰의 sub claim 을 이 헤더로 넣어준다.
     * study-service 에서 "누가 강제 종료를 요청했는지" 를 알 필요가 생길 때(감사 로그, 알림 등)
     * 이 값을 그대로 넘겨쓸 수 있도록 Feign 호출에도 함께 실어 보낸다.
     */
    @PostMapping("/{path}/force-close")
    public ResponseEntity<CommonApiResponse<StudyAdminDto>> forceCloseStudy(
            @PathVariable("path") String path,
            @RequestHeader("X-Account-Id") Long requesterId) {

        StudyAdminDto updated = studyAdminClient.forceCloseStudy(
                path,
                InternalHeaderConstants.SERVICE_NAME,
                requesterId);

        return ResponseEntity.ok(CommonApiResponse.ok("스터디가 강제 비공개 처리되었습니다.", updated));
    }
}