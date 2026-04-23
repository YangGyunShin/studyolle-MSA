package com.studyolle.account.controller;

import com.studyolle.account.common.EmailVerifiedGuard;
import com.studyolle.account.dto.request.*;
import com.studyolle.account.dto.response.AccountResponse;
import com.studyolle.account.dto.response.CommonApiResponse;
import com.studyolle.account.entity.Account;
import com.studyolle.account.service.AccountSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// api-gateway에서 검증 완료 후 X-Account-Id 헤더가 주입된다.
// 이 헤더가 없으면 api-gateway 단에서 이미 401로 차단됐으므로,
// 컨트롤러까지 도달했다면 헤더가 반드시 존재한다고 신뢰해도 된다.
@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountSettingsService accountSettingsService;

    // GET /api/accounts/me - 내 정보 조회
    @GetMapping("/me")
    public ResponseEntity<CommonApiResponse<AccountResponse>> getMyInfo(
            @RequestHeader("X-Account-Id") Long accountId) {

        Account account = accountSettingsService.getAccount(accountId);
        return ResponseEntity.ok(CommonApiResponse.ok(AccountResponse.from(account)));
    }

    // GET /api/accounts/{nickname} - 공개 프로필 조회
    @GetMapping("/{nickname}")
    public ResponseEntity<CommonApiResponse<AccountResponse>> getProfile(
            @PathVariable String nickname) {

        Account account = accountSettingsService.getAccountByNickname(nickname);
        return ResponseEntity.ok(CommonApiResponse.ok(AccountResponse.from(account)));
    }

    // PUT /api/accounts/settings/profile
    @PutMapping("/settings/profile")
    public ResponseEntity<CommonApiResponse<Void>> updateProfile(
            @RequestHeader("X-Account-Id") Long accountId,
            @RequestHeader("X-Account-Email-Verified") Boolean emailVerified,
            @Valid @RequestBody UpdateProfileRequest request) {

        EmailVerifiedGuard.require(emailVerified);

        accountSettingsService.updateProfile(accountId, request);
        return ResponseEntity.ok(CommonApiResponse.ok("프로필을 수정했습니다."));
    }

    // PUT /api/accounts/settings/password
    @PutMapping("/settings/password")
    public ResponseEntity<CommonApiResponse<Void>> updatePassword(
            @RequestHeader("X-Account-Id") Long accountId,
            @RequestHeader("X-Account-Email-Verified") Boolean emailVerified,
            @Valid @RequestBody UpdatePasswordRequest request) {

        EmailVerifiedGuard.require(emailVerified);

        if (!request.getNewPassword().equals(request.getNewPasswordConfirm())) {
            return ResponseEntity.badRequest()
                    .body(CommonApiResponse.ok("새 비밀번호가 일치하지 않습니다."));
        }
        accountSettingsService.updatePassword(accountId, request.getNewPassword());
        return ResponseEntity.ok(CommonApiResponse.ok("비밀번호를 변경했습니다."));
    }

    // PUT /api/accounts/settings/nickname
    @PutMapping("/settings/nickname")
    public ResponseEntity<CommonApiResponse<Void>> updateNickname(
            @RequestHeader("X-Account-Id") Long accountId,
            @RequestHeader("X-Account-Email-Verified") Boolean emailVerified,
            @Valid @RequestBody UpdateNicknameRequest request) {

        EmailVerifiedGuard.require(emailVerified);

        accountSettingsService.updateNickname(accountId, request.getNickname());
        return ResponseEntity.ok(CommonApiResponse.ok("닉네임을 변경했습니다."));
    }

    // PUT /api/accounts/settings/notifications
    @PutMapping("/settings/notifications")
    public ResponseEntity<CommonApiResponse<Void>> updateNotifications(
            @RequestHeader("X-Account-Id") Long accountId,
            @RequestHeader("X-Account-Email-Verified") Boolean emailVerified,
            @Valid @RequestBody UpdateNotificationsRequest request) {

        EmailVerifiedGuard.require(emailVerified);

        accountSettingsService.updateNotifications(accountId, request);
        return ResponseEntity.ok(CommonApiResponse.ok("알림 설정을 변경했습니다."));
    }

    // GET /api/accounts/settings/tags — 내 태그 목록 조회
    @GetMapping("/settings/tags")
    public ResponseEntity<CommonApiResponse<List<String>>> getTags(
            @RequestHeader("X-Account-Id") Long accountId) {

        Set<String> tag = accountSettingsService.getTags(accountId);
        List<String> tags = new ArrayList<>(tag);
        return ResponseEntity.ok(CommonApiResponse.ok(tags));
    }

    // POST /api/accounts/settings/tags/add
    @PostMapping("/settings/tags/add")
    public ResponseEntity<CommonApiResponse<Void>> addTag(
            @RequestHeader("X-Account-Id") Long accountId,
            @RequestHeader("X-Account-Email-Verified") Boolean emailVerified,
            @Valid @RequestBody TagRequest request) {

        EmailVerifiedGuard.require(emailVerified);

        accountSettingsService.addTag(accountId, request.getTagTitle());
        return ResponseEntity.ok(CommonApiResponse.ok("태그를 추가했습니다."));
    }

    // POST /api/accounts/settings/tags/remove
    @PostMapping("/settings/tags/remove")
    public ResponseEntity<CommonApiResponse<Void>> removeTag(
            @RequestHeader("X-Account-Id") Long accountId,
            @RequestHeader("X-Account-Email-Verified") Boolean emailVerified,
            @Valid @RequestBody TagRequest request) {

        EmailVerifiedGuard.require(emailVerified);

        accountSettingsService.removeTag(accountId, request.getTagTitle());
        return ResponseEntity.ok(CommonApiResponse.ok("태그를 삭제했습니다."));
    }

    // GET /api/accounts/settings/zones — 내 지역 목록 조회
    @GetMapping("/settings/zones")
    public ResponseEntity<CommonApiResponse<List<String>>> getZones(
            @RequestHeader("X-Account-Id") Long accountId) {

        Set<String> zone = accountSettingsService.getZones(accountId);
        List<String> zones = new ArrayList<>(zone);
        return ResponseEntity.ok(CommonApiResponse.ok(zones));
    }

    // POST /api/accounts/settings/zones/add
    @PostMapping("/settings/zones/add")
    public ResponseEntity<CommonApiResponse<Void>> addZone(
            @RequestHeader("X-Account-Id") Long accountId,
            @RequestHeader("X-Account-Email-Verified") Boolean emailVerified,
            @Valid @RequestBody ZoneRequest request) {

        EmailVerifiedGuard.require(emailVerified);

        accountSettingsService.addZone(accountId, request.getZoneName());
        return ResponseEntity.ok(CommonApiResponse.ok("지역을 추가했습니다."));
    }

    // POST /api/accounts/settings/zones/remove
    @PostMapping("/settings/zones/remove")
    public ResponseEntity<CommonApiResponse<Void>> removeZone(
            @RequestHeader("X-Account-Id") Long accountId,
            @RequestHeader("X-Account-Email-Verified") Boolean emailVerified,
            @Valid @RequestBody ZoneRequest request) {

        EmailVerifiedGuard.require(emailVerified);

        accountSettingsService.removeZone(accountId, request.getZoneName());
        return ResponseEntity.ok(CommonApiResponse.ok("지역을 삭제했습니다."));
    }
}