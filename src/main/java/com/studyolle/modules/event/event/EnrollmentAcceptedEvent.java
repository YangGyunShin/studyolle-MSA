package com.studyolle.modules.event.event;

import com.studyolle.modules.event.entity.Enrollment;

/**
 * 참가 신청 승인 시 발행되는 알림 메시지
 *
 * [발행 시점]
 * EventService.acceptEnrollment() 내부에서 발행된다:
 *
 *   public void acceptEnrollment(Event event, Enrollment enrollment) {
 *       event.accept(enrollment);                                    // 도메인 상태 변경
 *       eventPublisher.publishEvent(new EnrollmentAcceptedEvent(enrollment));  // 알림 발행
 *   }
 *
 * [수신자]
 * EnrollmentEventListener.handleEnrollmentEvent()가 수신한다.
 * 부모 타입인 EnrollmentEvent로 선언된 파라미터가 이 클래스의 인스턴스도 받을 수 있다.
 * (다형성: EnrollmentAcceptedEvent IS-A EnrollmentEvent)
 *
 * [메시지 내용]
 * 생성자에서 승인 안내 메시지를 고정값으로 설정한다.
 * 이 메시지가 이메일 본문과 웹 알림 텍스트에 그대로 사용된다.
 *
 * [전체 흐름]
 *   운영자가 승인 버튼 클릭
 *     → EnrollmentManageController.acceptEnrollment()
 *     → EventService.acceptEnrollment()
 *     → event.accept(enrollment)  -- 도메인 상태 변경 (accepted = true)
 *     → eventPublisher.publishEvent(new EnrollmentAcceptedEvent(enrollment))
 *     → Spring이 감지하여 EnrollmentEventListener.handleEnrollmentEvent() 호출
 *     → 이메일 발송 + 웹 알림 저장
 */
public class EnrollmentAcceptedEvent extends EnrollmentEvent {

    /**
     * @param enrollment 승인된 참가 신청 엔티티
     *
     * 부모 클래스 생성자에 enrollment와 승인 안내 메시지를 전달한다.
     * 메시지는 이 클래스에서 고정값으로 결정되므로, 발행 측(EventService)에서는
     * enrollment만 전달하면 된다.
     */
    public EnrollmentAcceptedEvent(Enrollment enrollment) {
        super(enrollment, "모임 참가 신청을 확인했습니다. 모임에 참석하세요.");
    }
}