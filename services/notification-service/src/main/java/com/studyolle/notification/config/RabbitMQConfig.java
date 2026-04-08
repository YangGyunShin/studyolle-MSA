package com.studyolle.notification.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ Exchange / Queue / Binding 을 Spring Bean 으로 선언하는 설정 클래스 (Consumer 측).
 *
 * =============================================
 * 전체 메시지 흐름
 * =============================================
 *
 * event-service (Producer)
 *      │
 *      │  rabbitTemplate.convertAndSend(
 *      │      "event.notification",    ← Exchange 이름
 *      │      "enrollment.accepted",   ← Routing Key
 *      │      enrollmentEventDto       ← 메시지 본문
 *      │  )
 *      ▼
 * [event.notification] ← Topic Exchange
 *      │  Routing Key 가 "enrollment.#" 패턴에 매칭됨
 *      ▼
 * [enrollment.queue] ← Queue
 *      │
 *      ▼
 * notification-service (Consumer) ← 이 클래스가 선언하는 측
 *      @RabbitListener(queues = "enrollment.queue")
 *
 * =============================================
 * 양쪽에서 동일하게 선언하는 이유
 * =============================================
 *
 * event-service 와 notification-service 두 곳 모두 동일한 Exchange / Queue / Binding 을 선언한다.
 * RabbitMQ 는 먼저 등록된 서비스가 Exchange 와 Queue 를 생성하고,
 * 나중에 등록된 서비스가 같은 이름으로 선언하면 이미 존재함을 확인하고 그대로 사용한다 (멱등성).
 * 두 서비스의 기동 순서에 관계없이 항상 정상 동작한다.
 *
 * =============================================
 * 상수를 public static final 로 선언한 이유
 * =============================================
 *
 * EnrollmentEventConsumer 의 @RabbitListener(queues = RabbitMQConfig.QUEUE) 처럼
 * 다른 클래스에서 이 상수를 참조한다.
 * 문자열 리터럴을 직접 쓰면 오타 발생 시 RabbitMQ 가 다른 Queue 를 찾아
 * 메시지를 수신하지 못하는데 에러 로그도 안 나와서 원인을 찾기 매우 어렵다.
 * 상수를 참조하면 컴파일 타임에 오타를 잡을 수 있다.
 */
@Configuration
public class RabbitMQConfig {

    /**
     * Topic Exchange 이름.
     * event-service 의 RabbitMQConfig.EXCHANGE 와 반드시 동일해야 한다.
     * 이름이 다르면 Producer 와 Consumer 가 서로 다른 Exchange 를 바라보게 된다.
     */
    public static final String EXCHANGE = "event.notification";

    /**
     * Consumer 가 메시지를 기다리는 Queue 이름.
     * @RabbitListener(queues = RabbitMQConfig.QUEUE) 에서 이 상수를 참조한다.
     */
    public static final String QUEUE    = "enrollment.queue";

    /**
     * Binding 패턴 — enrollment. 으로 시작하는 모든 Routing Key 수신.
     * enrollment.accepted / rejected / applied / attendance 모두 해당된다.
     * # 는 0개 이상의 단어를 매칭하므로 이벤트 타입이 늘어나도 Binding 변경이 필요 없다.
     */
    public static final String ROUTING  = "enrollment.#";

    /**
     * Topic Exchange Bean.
     *
     * TopicExchange 는 Routing Key 의 패턴 매칭으로 Queue 를 선택하는 Exchange 유형이다.
     * Spring 이 이 Bean 을 감지하면 애플리케이션 시작 시 RabbitMQ 에 Exchange 를 자동 선언한다.
     * 이미 동일한 이름의 Exchange 가 존재하면 그대로 사용한다 (멱등성).
     */
    @Bean
    public TopicExchange notificationExchange() {
        return new TopicExchange(EXCHANGE);
    }

    /**
     * enrollment 이벤트 Queue Bean.
     *
     * durable(true) — RabbitMQ 서버가 재시작되어도 Queue 정의가 사라지지 않는다.
     * durable(false) 면 서버 재시작 시 Queue 자체가 삭제되어
     * Consumer 가 연결하려 해도 Queue 가 없어서 에러가 발생한다.
     */
    @Bean
    public Queue enrollmentQueue() {
        return QueueBuilder.durable(QUEUE).build();
    }

    /**
     * Exchange ↔ Queue 연결 Binding Bean.
     *
     * "이 Exchange 에서 이 패턴의 Routing Key 로 오는 메시지를 이 Queue 로 보내라" 는 규칙이다.
     * Binding 없이는 Exchange 에 메시지를 전달해도 Queue 로 도달하지 않는다.
     *
     * 이 Binding 의 의미:
     *   [event.notification Exchange] 에서
     *   [enrollment.# 패턴에 매칭되는 Routing Key] 로 오는 메시지를
     *   [enrollment.queue] 로 라우팅하라.
     */
    @Bean
    public Binding enrollmentBinding() {
        return BindingBuilder
                .bind(enrollmentQueue())
                .to(notificationExchange())
                .with(ROUTING);
    }

    /**
     * JSON MessageConverter Bean.
     *
     * 이 Bean 이 등록되면 @RabbitListener 가 수신한 JSON 문자열을
     * EnrollmentEventDto 객체로 자동 역직렬화한다.
     * 이 Bean 이 없으면 RabbitMQ 기본 직렬화(Java 바이너리)를 사용하므로
     * Producer 가 JSON 으로 보낸 메시지를 역직렬화하지 못해 에러가 발생한다.
     *
     * Spring Boot 는 이 Bean 이 등록되어 있으면 @RabbitListener 에 자동으로 적용한다.
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
