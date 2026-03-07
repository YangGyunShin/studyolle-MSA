package com.studyolle.modules.event.event;

import com.studyolle.modules.event.entity.Enrollment;

/**
 * 참가 신청 거절 시 발행되는 알림 메시지
 *
 * [발행 시점]
 * EventService.rejectEnrollment() 내부에서 발행된다:
 *
 *   public void rejectEnrollment(Event event, Enrollment enrollment) {
 *       event.reject(enrollment);                                    // 도메인 상태 변경
 *       eventPublisher.publishEvent(new EnrollmentRejectedEvent(enrollment));  // 알림 발행
 *   }
 *
 * [수신자]
 * EnrollmentEventListener.handleEnrollmentEvent()가 수신한다.
 * AcceptedEvent와 동일한 핸들러가 처리한다. (부모 타입 EnrollmentEvent로 수신)
 *
 * [AcceptedEvent와의 차이점]
 * 클래스 구조는 동일하고 메시지 내용만 다르다:
 *   - AcceptedEvent: "모임 참가 신청을 확인했습니다. 모임에 참석하세요."
 *   - RejectedEvent: "모임 참가 신청을 거절했습니다."
 *
 * 그럼에도 별도 클래스로 분리한 이유:
 *   1. 타입으로 사건의 종류를 명확히 구분할 수 있다
 *      → 로그에서 "EnrollmentRejectedEvent 발행"만 보고도 거절 사건임을 알 수 있음
 *   2. 나중에 거절 시에만 특별한 처리가 필요해질 경우 독립적으로 확장 가능
 *      → 예: 거절 시 대기자에게 추가 안내 메일 발송 등
 *
 * [전체 흐름]
 *   운영자가 거절 버튼 클릭
 *     → EnrollmentManageController.rejectEnrollment()
 *     → EventService.rejectEnrollment()
 *     → event.reject(enrollment)  -- 도메인 상태 변경 (accepted = false)
 *     → eventPublisher.publishEvent(new EnrollmentRejectedEvent(enrollment))
 *     → Spring이 감지하여 EnrollmentEventListener.handleEnrollmentEvent() 호출
 *     → 이메일 발송 + 웹 알림 저장
 */
public class EnrollmentRejectedEvent extends EnrollmentEvent {

    /**
     * @param enrollment 거절된 참가 신청 엔티티
     */
    public EnrollmentRejectedEvent(Enrollment enrollment) {
        super(enrollment, "모임 참가 신청을 거절했습니다.");
    }
}