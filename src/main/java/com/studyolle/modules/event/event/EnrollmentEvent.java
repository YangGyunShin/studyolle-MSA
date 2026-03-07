package com.studyolle.modules.event.event;

import com.studyolle.modules.event.entity.Enrollment;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 참가 신청 상태 변경 시 발행되는 알림 메시지의 공통 추상 클래스
 *
 * [이 클래스의 역할]
 *
 * 참가 신청이 승인되거나 거절되었을 때, 그 사실을 알림 시스템으로 전달하기 위한
 * "메시지 봉투" 역할을 한다. 실제 편지(구체적인 사건)는 하위 클래스가 담당하고,
 * 이 클래스는 봉투의 공통 양식(누구의 신청인지, 어떤 메시지인지)을 정의한다.
 *
 *   EnrollmentEvent (추상 - 공통 메시지 구조)
 *     ├── EnrollmentAcceptedEvent (승인 알림 메시지)
 *     └── EnrollmentRejectedEvent (거절 알림 메시지)
 *
 * [왜 추상 클래스로 설계했는가?]
 *
 * 승인과 거절은 전달하는 데이터 구조(enrollment + message)가 동일하고,
 * 메시지 내용만 다르다. 공통 구조를 추상 클래스로 뽑아내면:
 *
 *   1. 리스너에서 하나의 타입(EnrollmentEvent)으로 두 이벤트를 모두 수신할 수 있다.
 *      → handleEnrollmentEvent(EnrollmentEvent event) 하나로 승인/거절 모두 처리
 *      → 이벤트 종류마다 별도 핸들러를 만들 필요가 없다
 *
 *   2. 새로운 이벤트 타입이 추가되어도 리스너 코드를 변경할 필요가 없다.
 *      → 예: EnrollmentCancelledEvent를 추가해도 같은 핸들러가 자동으로 수신
 *
 * [Spring ApplicationEvent 시스템에서의 위치]
 *
 * 이 클래스 자체는 Spring의 ApplicationEvent를 상속하지 않는 POJO이다.
 * Spring 4.2+부터는 ApplicationEvent를 상속하지 않아도
 * eventPublisher.publishEvent(객체)로 발행하면 @EventListener가 수신할 수 있다.
 *
 * 전체 흐름에서의 위치:
 *
 *   EventService (발행자)
 *     → eventPublisher.publishEvent(new EnrollmentAcceptedEvent(enrollment))
 *     → Spring이 EnrollmentEvent 타입의 @EventListener를 찾아서 자동 호출
 *     → EnrollmentEventListener.handleEnrollmentEvent() 실행
 */
@Getter
@RequiredArgsConstructor
public abstract class EnrollmentEvent {

    /**
     * 상태 변경이 발생한 참가 신청(Enrollment) 엔티티
     *
     * - 이 객체를 통해 리스너가 필요한 모든 정보에 접근할 수 있다:
     *   enrollment.getAccount()  → 알림을 받을 사용자
     *   enrollment.getEvent()    → 어떤 모임인지
     *   enrollment.getEvent().getStudy() → 어떤 스터디인지
     *
     * - protected final: 하위 클래스에서 직접 접근 가능하되, 외부에서 변경 불가
     */
    protected final Enrollment enrollment;

    /**
     * 사용자에게 전달할 알림 메시지 내용
     *
     * - 이메일 본문, 웹 알림 텍스트 등에 그대로 사용된다.
     * - 구체적인 메시지 내용은 하위 클래스의 생성자에서 결정:
     *   AcceptedEvent → "모임 참가 신청을 확인했습니다. 모임에 참석하세요."
     *   RejectedEvent → "모임 참가 신청을 거절했습니다."
     */
    protected final String message;
}