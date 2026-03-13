package com.studyolle.modules.event.controller;

import com.studyolle.modules.account.entity.Account;
import com.studyolle.modules.event.entity.Enrollment;
import com.studyolle.modules.event.entity.Event;
import com.studyolle.modules.event.service.EnrollmentService;
import com.studyolle.modules.study.service.StudySettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * ============================================================
 * 운영자(스터디 리더)의 참가 신청 관리 전용 컨트롤러
 * ============================================================
 *
 * [담당 범위]
 * - 스터디 운영자가 참가 신청(Enrollment)을 승인/거절/출석 처리하는 관리 기능
 * - Enrollment 엔티티의 상태를 "관리자 관점"에서 변경
 *
 * [분리 기준]
 * - 사용자 역할: 스터디 운영자(리더/관리자)만 사용
 * - 권한 패턴: 일관되게 StudyService.getStudyToUpdate() 사용
 *   -> 스터디 존재 여부 + 운영자 권한까지 검증
 * - 대상 엔티티: Enrollment (타인의 참가 신청을 관리)
 *
 * [일반 사용자의 참가 신청과의 차이점]
 * ---------------------------------------------------------------
 *  구분              | EnrollmentController    | EnrollmentManageController
 * ---------------------------------------------------------------
 *  사용자            | 일반 참가자              | 스터디 운영자(리더)
 *  행위              | 본인 신청/취소           | 타인 승인/거절/출석 관리
 *  권한 검증         | getStudyToEnroll        | getStudyToUpdate
 *  이벤트 방식       | FCFS/CONFIRMATIVE 공통  | 주로 CONFIRMATIVE에서 활용
 * ---------------------------------------------------------------
 *
 * [URL 패턴]
 * - GET /study/{path}/events/{eventId}/enrollments/{enrollmentId}/accept        (승인)
 * - GET /study/{path}/events/{eventId}/enrollments/{enrollmentId}/reject        (거절)
 * - GET /study/{path}/events/{eventId}/enrollments/{enrollmentId}/checkin       (출석)
 * - GET /study/{path}/events/{eventId}/enrollments/{enrollmentId}/cancel-checkin (출석 취소)
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/study/{path}")
public class EnrollmentManageController {

    private final StudySettingsService studySettingsService;
    private final EnrollmentService enrollmentService;

    /**
     * 참가 신청 승인 처리
     *
     * - GET /study/{path}/events/{eventId}/enrollments/{enrollmentId}/accept
     * - CONFIRMATIVE 방식 이벤트에서 운영자가 대기 중인 신청자를 수동 승인
     *
     * =======================
     * 전체 데이터 흐름
     * =======================
     * GET .../enrollments/{enrollmentId}/accept
     *  -> StudyService.getStudyToUpdate() (운영자 권한 검증)
     *  -> EventService.acceptEnrollment() 호출
     *     -> Event.accept() (도메인 상태전이: 정원 확인 -> 승인)
     *     -> EnrollmentAcceptedEvent 발행 (알림 후처리)
     *  -> 이벤트 상세 화면으로 리다이렉트
     */
    @GetMapping("/events/{eventId}/enrollments/{enrollmentId}/accept")
    public String acceptEnrollment(Account account,
                                   @PathVariable String path,
                                   @PathVariable("enrollmentId") Enrollment enrollment,
                                   @PathVariable("eventId") Event event) {

        Study study = studySettingsService.getStudyToUpdate(account, path);
        enrollmentService.acceptEnrollment(event, enrollment);

        return "redirect:/study/" + study.getEncodedPath() + "/events/" + event.getId();
    }

    /**
     * 참가 신청 거절 처리
     *
     * - GET /study/{path}/events/{eventId}/enrollments/{enrollmentId}/reject
     * - CONFIRMATIVE 방식에서 운영자가 대기 중인 신청자를 거절
     *
     * =======================
     * 전체 데이터 흐름
     * =======================
     * GET .../enrollments/{enrollmentId}/reject
     *  -> StudyService.getStudyToUpdate() (운영자 권한 검증)
     *  -> EventService.rejectEnrollment() 호출
     *     -> Event.reject() (도메인 상태전이: 승인 상태 취소)
     *     -> EnrollmentRejectedEvent 발행 (알림 후처리)
     *  -> 이벤트 상세 화면으로 리다이렉트
     */
    @GetMapping("/events/{eventId}/enrollments/{enrollmentId}/reject")
    public String rejectEnrollment(Account account,
                                   @PathVariable String path,
                                   @PathVariable("enrollmentId") Enrollment enrollment,
                                   @PathVariable("eventId") Event event) {

        Study study = studySettingsService.getStudyToUpdate(account, path);
        enrollmentService.rejectEnrollment(event, enrollment);

        return "redirect:/study/" + study.getEncodedPath() + "/events/" + event.getId();
    }

    /**
     * 참가자 출석 처리
     *
     * - GET /study/{path}/events/{eventId}/enrollments/{enrollmentId}/checkin
     * - 이미 승인된 Enrollment에 대해 출석 여부를 true로 설정
     * - JPA dirty checking으로 자동 update 쿼리 실행
     */
    @GetMapping("/events/{eventId}/enrollments/{enrollmentId}/checkin")
    public String checkInEnrollment(Account account,
                                    @PathVariable String path,
                                    @PathVariable("enrollmentId") Enrollment enrollment,
                                    @PathVariable("eventId") Event event) {

        Study study = studySettingsService.getStudyToUpdate(account, path);
        enrollmentService.checkInEnrollment(enrollment);

        return "redirect:/study/" + study.getEncodedPath() + "/events/" + event.getId();
    }

    /**
     * 참가자 출석 취소 처리
     *
     * - GET /study/{path}/events/{eventId}/enrollments/{enrollmentId}/cancel-checkin
     * - 운영자가 실수로 출석 처리한 경우 다시 취소
     */
    @GetMapping("/events/{eventId}/enrollments/{enrollmentId}/cancel-checkin")
    public String cancelCheckInEnrollment(Account account,
                                          @PathVariable String path,
                                          @PathVariable("enrollmentId") Enrollment enrollment,
                                          @PathVariable("eventId") Event event) {

        Study study = studySettingsService.getStudyToUpdate(account, path);
        enrollmentService.cancelCheckInEnrollment(enrollment);

        return "redirect:/study/" + study.getEncodedPath() + "/events/" + event.getId();
    }
}