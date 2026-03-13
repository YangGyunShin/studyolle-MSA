package com.studyolle.modules.admin.service;

import com.studyolle.modules.account.entity.Account;
import com.studyolle.modules.account.repository.AccountRepository;
import com.studyolle.modules.admin.dto.*;
import com.studyolle.modules.study.repository.StudyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 기능 전용 서비스
 *
 * - AdminController 전담 서비스 (단일 책임 원칙)
 * - 다른 서비스(StudyService 등)를 직접 호출하지 않고
 *   Repository에서 직접 조회 (관리 기능 특성상 별도 트랜잭션 범위 적용)
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AdminService {

    private final AccountRepository accountRepository;
    private final StudyRepository studyRepository;

    // ──────────────────────────────────────────────
    // 대시보드
    // ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AdminDashboardStats getDashboardStats() {
        long totalMembers = accountRepository.count();
        long totalStudies = studyRepository.count();
        // TODO: event, today 가입자 등은 추가 쿼리 필요
        return new AdminDashboardStats(totalMembers, totalStudies, 0L, 0L);
    }

    @Transactional(readOnly = true)
    public List<Account> getRecentMembers(int limit) {
        // TODO: findTop5ByOrderByJoinedAtDesc() 메서드 AccountRepository에 추가 필요
        return accountRepository.findAll().stream()
                .filter(a -> a.getJoinedAt() != null)
                .sorted((a, b) -> b.getJoinedAt().compareTo(a.getJoinedAt()))
                .limit(limit)
                .toList();
    }

    // ──────────────────────────────────────────────
    // 회원 관리
    // ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<Account> searchMembers(String keyword, Pageable pageable) {
        if (keyword.isBlank()) {
            return accountRepository.findAll(pageable);
        }
        // TODO: AccountRepository에 검색 메서드 추가 필요
        // findByNicknameContainingIgnoreCaseOrEmailContainingIgnoreCase(keyword, keyword, pageable)
        return accountRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Account getMemberDetail(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));
    }

    public void forceVerifyEmail(Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));
        account.setEmailVerified(true);
        // joinedAt이 null인 경우 현재 시각으로 설정
        if (account.getJoinedAt() == null) {
            account.setJoinedAt(LocalDateTime.now());
        }
    }

    public boolean toggleAccountEnabled(Long id) {
        // TODO: Account에 enabled 필드 추가 시 구현
        // 현재는 role 변경으로 간접 비활성화 가능
        // 향후: account.setEnabled(!account.isEnabled())
        throw new UnsupportedOperationException("Account.enabled 필드 추가 후 구현 예정");
    }

    public void changeRole(Long id, String role) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));
        // 허용된 역할만 설정
        if (!List.of("ROLE_USER", "ROLE_ADMIN").contains(role)) {
            throw new IllegalArgumentException("유효하지 않은 역할입니다: " + role);
        }
        account.setRole(role);
    }

    // ──────────────────────────────────────────────
    // 스터디 관리
    // ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<Study> searchStudies(String keyword, Pageable pageable) {
        if (keyword.isBlank()) {
            return studyRepository.findAll(pageable);
        }
        // TODO: StudyRepository에 title/path 검색 메서드 추가 필요
        return studyRepository.findAll(pageable);
    }

    public void forceCloseStudy(Long id) {
        Study study = studyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 스터디입니다."));
        study.setClosed(true);
        study.setClosedDateTime(LocalDateTime.now());
    }

    // ──────────────────────────────────────────────
    // 통계
    // ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AdminStatDto> getSignupStats(String period) {
        // TODO: JPQL or Native Query로 날짜별 집계 구현
        // SELECT DATE_TRUNC('month', joined_at), COUNT(*) FROM account GROUP BY 1 ORDER BY 1
        return List.of();
    }

    @Transactional(readOnly = true)
    public List<AdminStatDto> getStudyCreationStats(String period) {
        // TODO: 스터디 개설일 기준 집계
        return List.of();
    }

    @Transactional(readOnly = true)
    public List<AdminStatDto> getEventStats(String period) {
        // TODO: 모임 생성일 기준 집계
        return List.of();
    }
}