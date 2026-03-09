package com.studyolle.account.service;

import com.studyolle.account.dto.request.UpdateNotificationsRequest;
import com.studyolle.account.dto.request.UpdateProfileRequest;
import com.studyolle.account.entity.Account;
import com.studyolle.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// [모노리틱과의 차이]
// - @CurrentUser Account (Detached) → accountId Long (X-Account-Id 헤더에서 추출)
// - findById()로 영속 상태의 Account를 조회하므로 save() 없이 Dirty Checking으로 DB 반영
// - updateNickname() 내 accountAuthService.login() 제거 (세션 갱신 불필요)
// - ModelMapper 제거: 필드를 직접 set하는 방식으로 변경 (명시적으로 더 안전)
@Service
@Transactional
@RequiredArgsConstructor
public class AccountSettingsService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    public Account getAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. id=" + accountId));
    }

    public Account getAccountByNickname(String nickname) {
        Account account = accountRepository.findByNickname(nickname);
        if (account == null) {
            throw new IllegalArgumentException(nickname + "에 해당하는 사용자가 없습니다.");
        }
        return account;
    }

    public void updateProfile(Long accountId, UpdateProfileRequest request) {
        Account account = getAccount(accountId);
        account.setBio(request.getBio());
        account.setUrl(request.getUrl());
        account.setOccupation(request.getOccupation());
        account.setLocation(request.getLocation());
        account.setProfileImage(request.getProfileImage());
        // @Transactional + findById() → 영속 상태 → Dirty Checking → 자동 UPDATE
    }

    public void updatePassword(Long accountId, String newPassword) {
        Account account = getAccount(accountId);
        account.setPassword(passwordEncoder.encode(newPassword));
    }

    public void updateNickname(Long accountId, String newNickname) {
        if (accountRepository.existsByNickname(newNickname)) {
            throw new IllegalArgumentException("이미 사용 중인 닉네임입니다.");
        }
        Account account = getAccount(accountId);
        account.setNickname(newNickname);
        // [모노리틱 차이] accountAuthService.login() 호출 제거
        // 닉네임이 JWT에 포함되므로 변경 후 다음 로그인 시 새 토큰에 자동 반영됨
    }

    public void updateNotifications(Long accountId, UpdateNotificationsRequest request) {
        Account account = getAccount(accountId);
        account.setStudyCreatedByEmail(request.isStudyCreatedByEmail());
        account.setStudyCreatedByWeb(request.isStudyCreatedByWeb());
        account.setStudyEnrollmentResultByEmail(request.isStudyEnrollmentResultByEmail());
        account.setStudyEnrollmentResultByWeb(request.isStudyEnrollmentResultByWeb());
        account.setStudyUpdatedByEmail(request.isStudyUpdatedByEmail());
        account.setStudyUpdatedByWeb(request.isStudyUpdatedByWeb());
    }
}