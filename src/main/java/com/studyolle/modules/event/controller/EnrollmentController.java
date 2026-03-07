package com.studyolle.modules.event.controller;

import com.studyolle.modules.account.entity.Account;
import com.studyolle.modules.account.security.CurrentUser;
import com.studyolle.modules.event.entity.Event;
import com.studyolle.modules.event.service.EnrollmentService;
import com.studyolle.modules.study.entity.Study;
import com.studyolle.modules.study.service.StudyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * ============================================================
 * 일반 사용자의 이벤트 참가 신청/취소 전용 컨트롤러
 * ============================================================
 *
 * [담당 범위]
 * - 스터디 멤버(일반 참가자)가 이벤트에 참가 신청(enroll)하거나 취소(disenroll)하는 요청
 * - Enrollment 엔티티의 생성과 삭제를 "참가자 관점"에서 처리
 *
 * [분리 기준]
 * - 사용자 역할: 일반 스터디 멤버 (운영자 권한 불필요)
 * - 권한 패턴: 일관되게 StudyService.getStudyToEnroll() 사용
 *   -> 스터디 존재 여부 + 참가 가능 상태만 확인 (운영자 권한 검사 없음)
 * - 대상 엔티티: Enrollment (Event가 아님)
 *
 * [운영자의 참가 관리와의 차이점]
 * ---------------------------------------------------------------
 *  구분              | EnrollmentController  | EnrollmentManageController
 * ---------------------------------------------------------------
 *  사용자             | 일반 참가자             | 스터디 운영자(리더)
 *  행위              | 본인 신청/취소           | 타인 승인/거절/출석 관리
 *  권한 검증          | getStudyToEnroll      | getStudyToUpdate
 *  Enrollment 접근   | 본인 것만               | 모든 참가 신청
 * ---------------------------------------------------------------
 *
 * [URL 패턴]
 * - POST /study/{path}/events/{id}/enroll    (참가 신청)
 * - POST /study/{path}/events/{id}/disenroll (참가 취소)
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/study/{path}")
public class EnrollmentController {

    private final StudyService studyService;
    private final EnrollmentService enrollmentService;

    /**
     * 이벤트 참가 신청 처리
     *
     * - POST /study/{path}/events/{id}/enroll
     * - 현재 로그인한 사용자가 해당 이벤트에 참가 신청을 등록
     *
     * =======================
     * 전체 데이터 흐름
     * =======================
     * POST /study/{path}/events/{id}/enroll
     *  -> StudyService.getStudyToEnroll() (스터디 존재 + 참가 가능 확인)
     *  -> EventService.newEnrollment() 호출
     *     -> 중복 신청 여부 확인
     *     -> 신규 Enrollment 생성 (FCFS이면 즉시 승인, 아니면 대기)
     *     -> DB 저장
     *  -> 이벤트 상세페이지로 리다이렉트 (PRG 패턴)
     */
    @PostMapping("/events/{id}/enroll")
    public String newEnrollment(@CurrentUser Account account,
                                @PathVariable String path,
                                @PathVariable("id") Event event) {

        Study study = studyService.getStudyToEnroll(path);
        enrollmentService.newEnrollment(event, account);

        return "redirect:/study/" + study.getEncodedPath() + "/events/" + event.getId();
    }

    /**
     * 이벤트 참가 신청 취소 처리
     *
     * - POST /study/{path}/events/{id}/disenroll
     * - 현재 로그인한 사용자가 기존 참가 신청을 취소
     *
     * =======================
     * 전체 데이터 흐름
     * =======================
     * POST /study/{path}/events/{id}/disenroll
     *  -> StudyService.getStudyToEnroll() (스터디 존재 확인)
     *  -> EventService.cancelEnrollment() 호출
     *     -> 기존 Enrollment 조회 (event + account 기준)
     *     -> 출석 처리된 경우 취소 불가
     *     -> Enrollment 삭제
     *     -> FCFS일 경우 대기자 자동 승격 (acceptNextWaitingEnrollment)
     *  -> 이벤트 상세페이지로 리다이렉트
     */
    @PostMapping("/events/{id}/disenroll")
    public String cancelEnrollment(@CurrentUser Account account,
                                   @PathVariable String path,
                                   @PathVariable("id") Event event) {

        Study study = studyService.getStudyToEnroll(path);
        enrollmentService.cancelEnrollment(event, account);

        return "redirect:/study/" + study.getEncodedPath() + "/events/" + event.getId();
    }
}