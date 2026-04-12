package com.studyolle.admin.controller;

import com.studyolle.admin.client.AccountAdminClient;
import com.studyolle.admin.client.dto.AccountAdminDto;
import com.studyolle.admin.client.dto.PageResponse;
import com.studyolle.admin.common.InternalHeaderConstants;
import com.studyolle.admin.dto.response.CommonApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 관리자 회원 관리 REST API.
 *
 * [경로 정책]
 * 모든 관리자 API 는 /api/admin/** 아래에 둔다. api-gateway 에서 이 경로 패턴은
 * 이미 JwtAuthenticationFilter + AdminRoleFilter 를 거치게 되어 있고, 이 서비스 내부에서는
 * AdminAuthInterceptor 가 한 번 더 검증한다. 컨트롤러 메서드에 별도 권한 어노테이션이 없는
 * 이유는 그 때문이다.
 *
 * [단순 위임 (pass-through) 의 의미]
 * 이 컨트롤러는 Feign 호출 결과를 거의 그대로 반환한다. "그럼 admin-service 가 있으나
 * 없으나 마찬가지 아니냐" 는 의문이 들 수 있는데, 현재는 맞다. 하지만 나중에 여러 서비스의
 * 데이터를 조합하는 기능(예: 회원 + 스터디 + 알림 통계) 을 추가할 때 이 서비스가 있어야
 * 프론트가 한 번의 호출로 집계 데이터를 받을 수 있다. 단순 위임은 구조를 미리 만들어 두는
 * 투자이지 낭비가 아니다.
 *
 * [응답 타입이 중첩 제네릭인 이유]
 * ResponseEntity<CommonApiResponse<PageResponse<AccountAdminDto>>> 는
 * 세 겹의 포장을 그대로 드러낸다:
 *   1. ResponseEntity       — HTTP 상태 코드와 헤더를 담는 Spring 의 HTTP 응답 래퍼
 *   2. CommonApiResponse    — "success/message/data" 비즈니스 계약 래퍼
 *   3. PageResponse         — 페이지네이션 메타데이터 래퍼
 *   4. AccountAdminDto      — 실제 도메인 데이터
 * 못생기지만 각 계층의 책임이 다르므로 합쳐질 수 없는 구조다.
 */
@RestController
@RequestMapping("/api/admin/members")
@RequiredArgsConstructor
public class AdminMemberController {

    private final AccountAdminClient accountAdminClient;

    /**
     * GET /api/admin/members?keyword=xxx&page=0&size=20
     *
     * 최종 JSON 응답 모양:
     * {
     *   "success": true,
     *   "data": {
     *     "content": [ {id, nickname, email, ...}, ... ],
     *     "totalElements": 123,
     *     "totalPages": 7,
     *     "number": 0,
     *     "size": 20
     *   }
     * }
     */
    @GetMapping
    public ResponseEntity<CommonApiResponse<PageResponse<AccountAdminDto>>> listMembers(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        // Feign 호출 — 이 메서드는 HTTP 로 변환되어 account-service 로 나간다.
        // 실패하면 FeignException 이 던져지고, GlobalExceptionHandler 가 받아서
        // 502 Bad Gateway + 사용자 친화적 메시지로 변환한다.
        PageResponse<AccountAdminDto> result = accountAdminClient.listAccounts(
                keyword,
                page,
                size,
                InternalHeaderConstants.SERVICE_NAME
        );

        // CommonApiResponse 로 감싸서 반환 — 다른 public API 와 같은 구조
        return ResponseEntity.ok(CommonApiResponse.ok(result));
    }
}