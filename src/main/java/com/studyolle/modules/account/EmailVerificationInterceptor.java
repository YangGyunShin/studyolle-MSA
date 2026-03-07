package com.studyolle.modules.account;

import com.studyolle.modules.account.entity.Account;
import com.studyolle.modules.account.security.UserAccount;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 이메일 미인증 사용자의 특정 기능 접근을 제한하는 인터셉터
 *
 * =====================================================
 * [설계 방향: Spring Security 역할 분리 vs 인터셉터]
 *
 * 두 가지 접근 방식을 검토했음:
 *
 * (1) Spring Security 역할 분리 (ROLE_UNVERIFIED / ROLE_USER)
 *     - UserAccount 생성 시 인증 여부에 따라 다른 권한 부여
 *     - SecurityConfig에서 URL별 권한 체크
 *     - 장점: SecurityConfig에서 한눈에 접근 정책이 보임
 *     - 단점: 로그인 시점에 권한이 고정되므로, 인증 완료 후 재로그인 필요
 *
 * (2) 인터셉터 방식 (현재 채택)
 *     - preHandle()에서 매 요청마다 account.isEmailVerified() 체크
 *     - 장점: 인증 완료 즉시 반영 (재로그인 불필요)
 *     - 장점: 기존 NotificationInterceptor와 동일한 패턴으로 일관성 유지
 *     - 단점: WebConfig에 인터셉터 등록 + 대상 URL 관리 필요
 *
 * 인터셉터 방식을 채택한 이유:
 *   - 사용자가 이메일 인증 후 바로 기능 사용 가능 (UX 우선)
 *   - 기존 아키텍처(WebConfig + HandlerInterceptor)와 자연스럽게 통합
 *
 * =====================================================
 * [제한 대상 URL] (WebConfig에서 addPathPatterns으로 등록)
 *
 *   - /new-study                    : 스터디 생성
 *   - /study/*​/join                 : 스터디 가입
 *   - /study/*​/leave                : 스터디 탈퇴
 *   - /study/*​/new-event            : 모임 생성
 *   - /study/*​/events/*​/enroll      : 모임 참가 신청
 *   - /study/*​/events/*​/disenroll   : 모임 참가 취소
 */
@Component
public class EmailVerificationInterceptor implements HandlerInterceptor {

    /**
     * 컨트롤러 실행 전에 이메일 인증 여부를 확인
     *
     * @return true: 인증 완료 → 요청 계속 진행
     *         false: 미인증 → 안내 페이지로 리다이렉트 + 요청 중단
     */
    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null
                && authentication.getPrincipal() instanceof UserAccount userAccount) {

            Account account = userAccount.getAccount();

            if (!account.isEmailVerified()) {
                // 미인증 → 안내 페이지로 리다이렉트
                response.sendRedirect("/email-verification-required");
                return false;  // 컨트롤러 실행 중단
            }
        }

        return true;  // 인증 완료 또는 비로그인 → 정상 진행
    }
}