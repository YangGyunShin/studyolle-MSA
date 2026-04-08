package com.studyolle.notification.rabbitmq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * event-service 가 RabbitMQ 로 발행하는 모임 신청 이벤트 DTO (Consumer 측 역직렬화용).
 *
 * =============================================
 * 중요: event-service 의 EnrollmentEventDto 와 필드가 동일해야 한다
 * =============================================
 *
 * RabbitMQ 는 메시지를 JSON 문자열로 전송한다.
 * Consumer 측(notification-service)에서 JSON 을 이 클래스로 역직렬화할 때
 * 필드 이름이 다르면 해당 필드는 null 로 채워진다.
 *
 * 필드 이름, 타입을 event-service 의 EnrollmentEventDto 와 반드시 동일하게 유지해야 한다.
 *
 * =============================================
 * 발행되는 이벤트 종류 (eventType 값 = Routing Key)
 * =============================================
 *
 * eventType                발행 시점
 * enrollment.applied       선착순 모임에 즉시 수락된 경우 (EnrollmentService.enroll)
 * enrollment.accepted      관리자가 신청을 수락한 경우   (EnrollmentService.acceptEnrollment)
 * enrollment.rejected      관리자가 신청을 거절한 경우   (EnrollmentService.rejectEnrollment)
 * enrollment.attendance    관리자가 출석을 확인한 경우   (EnrollmentService.checkIn)
 *
 * eventType 이 곧 RabbitMQ Routing Key 이므로
 * Exchange 에서 "enrollment.#" 패턴에 매칭되어 enrollment.queue 로 라우팅된다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnrollmentEventDto {

    /**
     * 이벤트 종류 (= Routing Key).
     * enrollment.accepted / rejected / applied / attendance
     * EnrollmentEventConsumer 에서 switch 문으로 분기 처리한다.
     */
    private String eventType;

    /** 모임 ID. 알림 링크 생성에 사용된다. */
    private Long eventId;

    /** 모임 제목. 알림 메시지 내용에 포함된다. */
    private String eventTitle;

    /** 스터디 경로. 알림 링크 "/study/{studyPath}/events/{eventId}" 생성에 사용된다. */
    private String studyPath;

    /**
     * 신청자 ID (알림 수신 대상).
     * 수락/거절/출석 모두 신청자에게 알림을 보낸다.
     */
    private Long enrollmentAccountId;

    /**
     * 수락/거절/출석을 처리한 관리자 ID.
     * 현재는 사용하지 않지만, 나중에 "누가 처리했는지" 알림에 표시할 때 사용 가능하다.
     */
    private Long managedByAccountId;

    /**
     * 이벤트 발생 시각.
     * 중복 방지 키 생성에 사용된다: "enrollment:{eventId}:{eventType}"
     */
    private LocalDateTime occurredAt;
}
