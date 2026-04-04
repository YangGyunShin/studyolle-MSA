package com.studyolle.event.rabbitmq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 모임 신청(enrollment) 이벤트를 RabbitMQ 로 발행하는 Producer.
 *
 * =============================================
 * RabbitTemplate 동작 방식
 * =============================================
 *
 * RabbitTemplate.convertAndSend(exchange, routingKey, message) 호출 시:
 *
 *   1. message(EnrollmentEventDto) 를 JSON 으로 직렬화
 *      → RabbitMQConfig 에 등록된 Jackson2JsonMessageConverter 가 자동 처리
 *
 *   2. exchange("event.notification") 로 메시지 전달
 *      → Producer 는 Queue 를 직접 모른다. Exchange 에만 던진다.
 *
 *   3. Exchange 가 routingKey(예: "enrollment.accepted") 를 확인
 *      → "enrollment.#" 패턴에 매칭됨
 *      → "enrollment.queue" 로 라우팅
 *
 *   4. notification-service 의 @RabbitListener 가 Queue 에서 메시지 수신
 *
 * =============================================
 * Routing Key 로 eventType 을 사용하는 이유
 * =============================================
 *
 * eventType 이 곧 Routing Key 다.
 * "enrollment.accepted", "enrollment.rejected" 처럼
 * 이벤트 종류가 Routing Key 에 그대로 드러나므로
 * Consumer 측에서 어떤 이벤트를 처리하는지 명확하게 파악할 수 있다.
 *
 * 또한 Consumer 가 특정 이벤트만 구독하고 싶다면
 * Queue Binding 을 "enrollment.accepted" 처럼 구체적으로 지정하면 된다.
 * 현재는 "enrollment.#" 으로 모든 enrollment 이벤트를 하나의 Queue 에서 받고 있다.
 *
 * =============================================
 * 전송 실패 시 예외를 던지지 않는 이유
 * =============================================
 *
 * 알림은 핵심 비즈니스 로직(신청 수락/거절/출석)의 부가 기능이다.
 * RabbitMQ 전송이 실패하더라도 수락/거절 자체는 이미 DB 에 반영된 상태다.
 *
 * 만약 여기서 예외를 던지면:
 *   → EnrollmentService 의 트랜잭션이 롤백됨
 *   → DB 에는 수락이 반영되었지만 롤백으로 취소되는 데이터 불일치 발생
 *   → 사용자 입장에서는 수락 버튼을 눌렀는데 오류가 뜨는 상황
 *
 * 따라서 전송 실패 시 로그만 남기고 예외를 삼킨다(swallow).
 * 알림 누락은 나중에 로그로 확인하고 재처리할 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EnrollmentRabbitProducer {

    /**
     * Spring 이 자동 주입하는 RabbitMQ 메시지 전송 템플릿.
     *
     * RabbitTemplate 은 RabbitMQ 와의 연결, 채널 관리, 직렬화를 추상화한 클래스다.
     * RabbitMQConfig 에 Jackson2JsonMessageConverter Bean 이 등록되어 있으므로
     * 별도 설정 없이 convertAndSend() 호출 시 자동으로 JSON 직렬화가 적용된다.
     */
    private final RabbitTemplate rabbitTemplate;

    /**
     * enrollment 이벤트를 RabbitMQ 로 발행한다.
     *
     * EnrollmentService 에서 아래 4가지 시점에 이 메서드를 호출한다:
     *   enrollment.applied    : 선착순 모임에 즉시 수락된 경우
     *   enrollment.accepted   : 관리자가 신청을 수락한 경우
     *   enrollment.rejected   : 관리자가 신청을 거절한 경우
     *   enrollment.attendance : 관리자가 출석을 확인한 경우
     *
     * try-catch 로 예외를 잡는 이유:
     *   RabbitMQ 서버가 일시적으로 다운되거나 네트워크 오류가 발생해도
     *   EnrollmentService 의 트랜잭션에 영향을 주지 않기 위함이다.
     *   전송 실패는 로그로 기록하여 나중에 확인할 수 있다.
     *
     * @param event 발행할 enrollment 이벤트 (eventType, eventId, enrollmentAccountId 등 포함)
     */
    public void send(EnrollmentEventDto event) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE,     // "event.notification" Exchange 로 전달
                    event.getEventType(),        // Routing Key = eventType (예: "enrollment.accepted")
                    event                        // Jackson2JsonMessageConverter 가 JSON 으로 자동 직렬화
            );
            log.info("[RabbitMQ] 전송 성공: routingKey={}, enrollmentAccountId={}",
                    event.getEventType(), event.getEnrollmentAccountId());
        } catch (Exception e) {
            // 전송 실패 시 예외를 던지지 않고 로그만 남긴다.
            // EnrollmentService 의 핵심 로직(수락/거절 DB 반영)은 이미 완료된 상태이므로
            // 알림 전송 실패가 트랜잭션 롤백으로 이어지면 안 된다.
            log.error("[RabbitMQ] 전송 실패: routingKey={}, error={}",
                    event.getEventType(), e.getMessage());
        }
    }
}