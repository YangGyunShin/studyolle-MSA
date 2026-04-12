package com.studyolle.account.controller;

import com.studyolle.account.dto.response.AccountResponse;
import com.studyolle.account.dto.response.AccountSummaryResponse;
import com.studyolle.account.entity.Account;
import com.studyolle.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class AccountInternalController {

    private final AccountRepository accountRepository;

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

        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("계정을 찾을 수 없습니다."));

        return ResponseEntity.ok(AccountSummaryResponse.from(account));
    }

    // GET /internal/accounts/{id}/full — 프로필/알림 설정 전체 조회 (frontend-service 설정 페이지용)
    @GetMapping("/internal/accounts/{id}/full")
    public ResponseEntity<AccountResponse> getAccountFull(
            @PathVariable Long id,
            @RequestHeader("X-Internal-Service") String internalService) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("계정을 찾을 수 없습니다."));
        return ResponseEntity.ok(AccountResponse.from(account));
    }

    // GET /internal/accounts/{id}/tags — 태그 목록 조회 (settings/tags 페이지 초기 로드용)
    @GetMapping("/internal/accounts/{id}/tags")
    public ResponseEntity<List<String>> getAccountTags(
            @PathVariable Long id,
            @RequestHeader("X-Internal-Service") String internalService) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("계정을 찾을 수 없습니다."));
        return ResponseEntity.ok(new ArrayList<>(account.getTags()));
    }

    // GET /internal/accounts/{id}/zones — 지역 목록 조회 (settings/zones 페이지 초기 로드용)
    @GetMapping("/internal/accounts/{id}/zones")
    public ResponseEntity<List<String>> getAccountZones(
            @PathVariable Long id,
            @RequestHeader("X-Internal-Service") String internalService) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("계정을 찾을 수 없습니다."));
        return ResponseEntity.ok(new ArrayList<>(account.getZones()));
    }

    // GET /internal/accounts/by-nickname/{nickname}
    @GetMapping("/internal/accounts/by-nickname/{nickname}")
    public ResponseEntity<AccountSummaryResponse> getAccountByNickname(
            @PathVariable String nickname,
            @RequestHeader("X-Internal-Service") String internalService) {
        Account account = accountRepository.findByNickname(nickname);
        if (account == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(AccountSummaryResponse.from(account));
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

        // keyword 가 있으면 email 또는 nickname 에 포함된 것만 검색
        // keyword 가 null/빈문자면 전체 조회
        Page<Account> page;
        if (keyword == null || keyword.isBlank()) {
            page = accountRepository.findAll(pageable);
        } else {
            page = accountRepository.findByEmailContainingOrNicknameContaining(keyword, keyword, pageable);
        }

        // Page<Account> → Page<AccountSummaryResponse> 변환
        // Page.map() 은 전체 페이지 메타데이터(totalElements 등)를 유지한 채 요소만 변환한다
        return ResponseEntity.ok(page.map(AccountSummaryResponse::from));
    }
}