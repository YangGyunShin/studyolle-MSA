package com.studyolle.account.controller;

import com.studyolle.account.dto.request.AccountBatchRequest;
import com.studyolle.account.dto.request.RoleUpdateRequest;
import com.studyolle.account.dto.response.AccountResponse;
import com.studyolle.account.dto.response.AccountSummaryResponse;
import com.studyolle.account.service.AccountInternalService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 내부 전용 (/internal/accounts/**) HTTP 어댑터.
 *
 * 모든 비즈니스 로직은 AccountInternalService 에 위임한다.
 * 이 컨트롤러는 다음만 책임진다:
 *   1) HTTP 경로/헤더/본문 파싱
 *   2) X-Internal-Service 헤더로 내부 호출 검증 (InternalRequestFilter 가 1차 처리)
 *   3) Service 호출 결과를 ResponseEntity 로 감싸 반환
 *
 * 외부에서 /internal/** 로 직접 접근하는 것은 api-gateway 가 전면 차단한다.
 */
@RestController
@RequiredArgsConstructor
public class AccountInternalController {

    private final AccountInternalService accountInternalService;

    /*
     * frontend-service의 HomeController가 로그인 상태 판별 후
     * 대시보드 렌더링을 위해 계정 요약 정보를 요청하는 내부 전용 API.
     *
     * X-Internal-Service 헤더로 내부 서비스 요청임을 확인한다.
     * 외부에서 /internal/** 직접 접근은 api-gateway에서 전면 차단된다.
     */
    @GetMapping("/internal/accounts/{id}")
    public ResponseEntity<AccountSummaryResponse> getAccountSummary(
            @PathVariable Long id,
            @RequestHeader("X-Internal-Service") String internalService) {

        return ResponseEntity.ok(accountInternalService.getAccountSummary(id));
    }

    // GET /internal/accounts/{id}/full — 프로필/알림 설정 전체 조회 (frontend-service 설정 페이지용)
    @GetMapping("/internal/accounts/{id}/full")
    public ResponseEntity<AccountResponse> getAccountFull(
            @PathVariable Long id,
            @RequestHeader("X-Internal-Service") String internalService) {

        return ResponseEntity.ok(accountInternalService.getAccountFull(id));
    }

    // GET /internal/accounts/{id}/tags — 태그 목록 조회 (settings/tags 페이지 초기 로드용)
    @GetMapping("/internal/accounts/{id}/tags")
    public ResponseEntity<List<String>> getAccountTags(
            @PathVariable Long id,
            @RequestHeader("X-Internal-Service") String internalService) {

        return ResponseEntity.ok(accountInternalService.getAccountTags(id));
    }

    // GET /internal/accounts/{id}/zones — 지역 목록 조회 (settings/zones 페이지 초기 로드용)
    @GetMapping("/internal/accounts/{id}/zones")
    public ResponseEntity<List<String>> getAccountZones(
            @PathVariable Long id,
            @RequestHeader("X-Internal-Service") String internalService) {

        return ResponseEntity.ok(accountInternalService.getAccountZones(id));
    }

    /*
     * 닉네임으로 계정 조회 — frontend-service 프로필 페이지에서 호출.
     *
     * 닉네임이 존재하지 않을 가능성이 정상 흐름의 일부이므로
     * service 가 Optional 을 반환한다. 여기서는 200/404 로 분기만 한다.
     */
    @GetMapping("/internal/accounts/by-nickname/{nickname}")
    public ResponseEntity<AccountSummaryResponse> getAccountByNickname(
            @PathVariable String nickname,
            @RequestHeader("X-Internal-Service") String internalService) {

        return accountInternalService.getAccountByNickname(nickname)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // GET /internal/accounts?page=0&size=20&keyword=xxx
    //
    // 관리자 전용 회원 목록 조회. admin-service 가 호출한다.
    //
    // [왜 Page 를 쓰는가]
    // 회원 수가 수백 수천이 넘어가면 한 번에 전부 반환하는 것은 메모리/네트워크 모두 낭비다.
    // Spring Data JPA 의 Pageable 을 받으면 클라이언트가 필요한 페이지만 요청할 수 있고,
    // 응답 DTO 에 totalElements / totalPages 도 함께 담을 수 있어 UI 페이징이 쉬워진다.
    //
    // [왜 AccountSummaryResponse 가 아닌 별도 DTO 가 필요 없는가]
    // 관리자도 결국 "회원을 식별하는 최소 정보(id, email, nickname, emailVerified, role)" 만 있으면 된다.
    // 이미 존재하는 AccountSummaryResponse 를 그대로 재사용한다.
    // (role 이 Summary 에 포함되어 있지 않다면 아래 응답에서 확인 후 추가 필요)
    @GetMapping("/internal/accounts")
    public ResponseEntity<Page<AccountSummaryResponse>> listAccounts(
            @RequestHeader("X-Internal-Service") String internalService,
            @RequestParam(required = false) String keyword,
            Pageable pageable) {

        return ResponseEntity.ok(accountInternalService.listAccounts(keyword, pageable));
    }

    /**
     * POST /internal/accounts/batch
     * <p>
     * 요청 본문: { "ids": [1, 5, 12, 27] }
     * 응답 본문: { "1": {...}, "5": {...}, "12": {...} }   ← id → AccountSummaryResponse 의 map
     * <p>
     * 주 사용처: study-service 가 스터디 멤버들의 nickname / profileImage 를 한 번에 가져올 때.
     * <p>
     * 왜 GET 이 아닌 POST 인가:
     * - GET 은 쿼리스트링으로 ?ids=1&ids=5&ids=12... 처럼 넘겨야 해서 URL 길이 제한 (보통 2048 바이트) 에 걸린다.
     * - 요청 본문에 JSON 배열을 넣으면 길이 제한이 실질적으로 사라진다.
     * - 또한 프로젝트 원칙 "PATCH 금지 + 전 계층 POST 통일" 과 일관된다.
     * <p>
     * 왜 응답 타입이 Map 인가:
     * - 호출 측이 accountId 로 O(1) 조회를 할 수 있다.
     * - 요청한 id 중 존재하지 않는 것이 섞여 있어도 map 에 키만 빠지는 형태로 자연스럽게 누락 처리된다.
     */
    @PostMapping("/internal/accounts/batch")
    public ResponseEntity<Map<Long, AccountSummaryResponse>> getAccountsBatch(
            @RequestBody AccountBatchRequest request,
            @RequestHeader("X-Internal-Service") String internalService) {

        return ResponseEntity.ok(accountInternalService.getAccountsBatch(request.ids()));
    }

    /**
     * POST /internal/accounts/{id}/role
     *
     * 요청 본문: { "role": "ROLE_ADMIN" } 또는 { "role": "ROLE_USER" }
     * 헤더:
     *   X-Internal-Service: admin-service  (InternalRequestFilter 가 검증)
     *   X-Account-Id: {요청자 id}          (자기 자신 권한 변경 방지에 사용)
     *
     * [X-Account-Id 헤더를 controller 에서 받는 이유]
     * "요청자가 누구인가" 는 service 계층의 비즈니스 검증에 필요한 값이다.
     * service 가 HttpServletRequest 를 직접 보면 layering 이 깨지므로,
     * controller 에서 헤더를 꺼내 일반 파라미터로 변환해 service 에 넘긴다.
     * 이런 식으로 controller 는 HTTP 와 도메인 사이의 통역자 역할만 맡는다.
     *
     * [컨트롤러는 service 위임 한 줄만 남는다]
     * 비즈니스 로직(검증 3종, 조회, 변경, DTO 변환) 은 전부 service 안에서 일어난다.
     * controller 가 단순 위임만 하는 것이 좋은 분리이며,
     * 나중에 같은 비즈니스 로직을 다른 호출 경로(예: 배치 작업, 다른 컨트롤러) 에서
     * 재사용할 때 그대로 가져다 쓸 수 있다.
     */
    @PostMapping("/internal/accounts/{id}/role")
    public ResponseEntity<AccountSummaryResponse> updateRole(
            @PathVariable Long id,
            @RequestBody RoleUpdateRequest request,
            @RequestHeader("X-Internal-Service") String internalService,
            @RequestHeader(value = "X-Account-Id", required = false) Long requesterId) {

        return ResponseEntity.ok(accountInternalService.updateRole(id, requesterId, request.role()));
    }
}