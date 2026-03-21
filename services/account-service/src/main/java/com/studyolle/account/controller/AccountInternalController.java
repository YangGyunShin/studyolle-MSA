package com.studyolle.account.controller;

import com.studyolle.account.dto.response.AccountSummaryResponse;
import com.studyolle.account.entity.Account;
import com.studyolle.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}