package com.studyolle.event.rabbitmq;

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
 * RabbitMQ Exchange / Queue / Binding 을 Spring Bean 으로 선언하는 설정 클래스.
 *
 * =============================================
 * 전체 메시지 흐름
 * =============================================
 *
 * event-service (Producer)
 *      │
 *      │  RabbitTemplate.convertAndSend(
 *      │      "event.notification",   ← Exchange 이름
 *      │      "enrollment.accepted",  ← Routing Key
 *      │      dto                     ← 메시지 본문
 *      │  )
 *      ▼
 * [event.notification] ← Topic Exchange
 *      │  Routing Key "enrollment.accepted" 가
 *      │  패턴 "enrollment.#" 에 매칭됨
 *      ▼
 * [enrollment.queue] ← Queue
 *      │
 *      ▼
 * notification-service (Consumer)
 *      @RabbitListener(queues = "enrollment.queue")
 *
 * =============================================
 * Producer / Consumer 양쪽에서 동일하게 선언하는 이유
 * =============================================
 *
 * RabbitMQ 는 Exchange 와 Queue 가 미리 존재해야 메시지를 라우팅할 수 있다.
 * 어느 서비스가 먼저 뜨더라도 문제없도록 Producer / Consumer 양쪽에서 동일하게 선언한다.
 *
 * 먼저 뜬 서비스가 Exchange / Queue 를 생성하고,
 * 나중에 뜬 서비스가 같은 이름으로 선언하면 RabbitMQ 가 이미 존재함을 확인하고 그대로 사용한다.
 * 이것이 멱등성(Idempotency)이다: 몇 번을 선언해도 결과가 동일하다.
 *
 * =============================================
 * Topic Exchange 를 선택한 이유
 * =============================================
 *
 * RabbitMQ Exchange 에는 4가지 종류가 있다:
 *   Direct  : Routing Key 가 정확히 일치할 때만 전달
 *   Topic   : 와일드카드 패턴(*, #)으로 유연하게 매칭
 *   Fanout  : Routing Key 무시, 연결된 모든 Queue 에 브로드캐스트
 *   Headers : 메시지 헤더 속성으로 라우팅
 *
 * Topic Exchange 를 선택한 이유:
 *   enrollment 관련 이벤트가 accepted / rejected / applied / attendance 등
 *   여러 종류로 늘어날 수 있다.
 *   "enrollment.#" 패턴 하나로 enrollment 로 시작하는 모든 Routing Key 를 수신할 수 있어
 *   새 이벤트 타입이 추가되어도 Queue / Binding 설정을 변경할 필요가 없다.
 *
 * =============================================
 * 상수를 public static final 로 선언한 이유
 * =============================================
 *
 * Exchange / Queue 이름을 문자열 리터럴로 직접 쓰면
 * Producer(EnrollmentRabbitProducer) 와 Config(RabbitMQConfig) 에서
 * 같은 문자열을 두 번 작성하게 된다.
 * 오타가 나면 메시지가 엉뚱한 곳으로 가거나 연결 자체가 안 되는데,
 * 로그에는 에러가 안 나와서 원인을 찾기 매우 어렵다.
 *
 * RabbitMQConfig.EXCHANGE 처럼 상수를 참조하면
 * 컴파일 타임에 오타를 잡을 수 있고, 이름 변경 시 한 곳만 수정하면 된다.
 */
@Configuration
public class RabbitMQConfig {

    /**
     * Topic Exchange 이름.
     *
     * Exchange 는 Producer 가 메시지를 발행하는 대상이다.
     * Producer 는 Queue 를 직접 지정하지 않고, Exchange 에 메시지를 던진다.
     * Exchange 가 Routing Key 를 보고 적절한 Queue 로 메시지를 전달한다.
     *
     * 이름을 "event.notification" 으로 지은 이유:
     * event-service 에서 발생한 이벤트를 notification 용도로 전달하는 Exchange 임을 명시한다.
     */
    public static final String EXCHANGE = "event.notification";

    /**
     * enrollment 이벤트를 담는 Queue 이름.
     *
     * Queue 는 메시지가 Consumer 에게 전달되기 전에 대기하는 버퍼다.
     * notification-service 의 @RabbitListener 가 이 이름의 Queue 를 감시한다.
     */
    public static final String QUEUE = "enrollment.queue";

    /**
     * Binding 에 사용하는 Routing Key 패턴.
     *
     * "enrollment.#" 의 의미:
     *   enrollment. 으로 시작하고 뒤에 무엇이든(0개 이상의 단어) 붙는 Routing Key 를 모두 수신한다.
     *
     * 매칭 예시:
     *   "enrollment.accepted"    → ✅ 매칭
     *   "enrollment.rejected"    → ✅ 매칭
     *   "enrollment.applied"     → ✅ 매칭
     *   "enrollment.attendance"  → ✅ 매칭
     *   "study.published"        → ❌ 매칭 안 됨
     *
     * "enrollment.*" 와의 차이:
     *   * 는 딱 하나의 단어만 매칭한다.
     *   # 는 0개 이상의 단어를 매칭한다.
     *   "enrollment.item.added" 같은 depth 가 늘어난 키도 # 는 매칭하지만 * 는 못 한다.
     */
    public static final String ROUTING = "enrollment.#";

    /**
     * Topic Exchange Bean 을 등록한다.
     *
     * TopicExchange 는 Routing Key 의 패턴 매칭으로 Queue 를 선택한다.
     * Spring 이 이 Bean 을 감지하면 애플리케이션 시작 시 RabbitMQ 에 Exchange 를 자동으로 선언한다.
     * 이미 동일한 이름의 Exchange 가 존재하면 그대로 사용한다.
     */
    @Bean
    public TopicExchange notificationExchange() {
        return new TopicExchange(EXCHANGE);
    }

    /**
     * enrollment 이벤트 Queue Bean 을 등록한다.
     *
     * durable(true) 로 생성하는 이유:
     *   RabbitMQ 서버가 재시작되어도 Queue 정의가 사라지지 않는다.
     *   durable(false) 면 서버 재시작 시 Queue 자체가 삭제되어
     *   Consumer 가 연결하려 해도 Queue 가 없어서 에러가 발생한다.
     *
     * durable 이 보장하는 것과 보장하지 않는 것:
     *   보장 O : 서버 재시작 후에도 Queue 정의(이름, 속성)가 유지된다.
     *   보장 X : Queue 안에 쌓인 메시지 자체는 별도로 Persistent 설정이 필요하다.
     *            메시지 레벨의 영속성은 Producer 에서 delivery_mode=2 로 설정한다.
     */
    @Bean
    public Queue enrollmentQueue() {
        return QueueBuilder.durable(QUEUE).build();
    }

    /**
     * Exchange 와 Queue 를 Routing Key 패턴으로 연결하는 Binding Bean 을 등록한다.
     *
     * Binding 은 "이 Exchange 에서 이 패턴의 Routing Key 로 오는 메시지를
     * 이 Queue 로 보내라" 는 규칙이다.
     *
     * Binding 없이 Exchange 와 Queue 만 만들면 메시지가 어디로 가야 할지 알 수 없어서
     * Exchange 에 메시지를 발행해도 Queue 에 도달하지 않는다.
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
     * RabbitMQ 메시지를 Java 객체 ↔ JSON 으로 자동 변환하는 MessageConverter Bean 을 등록한다.
     *
     * 이 Bean 을 등록하지 않으면 RabbitTemplate 은 기본적으로
     * Java 직렬화(Serializable) 방식으로 메시지를 직렬화한다.
     * Java 직렬화는 바이너리 포맷이라 다른 언어 서비스와 통신이 불가능하고,
     * RabbitMQ Management UI 에서도 메시지 내용을 읽을 수 없다.
     *
     * Jackson2JsonMessageConverter 를 등록하면:
     *   Producer 측 : EnrollmentEventDto 객체 → JSON 문자열로 자동 직렬화 후 전송
     *   Consumer 측 : JSON 문자열 → EnrollmentEventDto 객체로 자동 역직렬화 후 수신
     *
     * Spring Boot 는 이 Bean 이 등록되어 있으면 RabbitTemplate 과 @RabbitListener 에
     * 자동으로 적용한다. 별도 설정 없이 convertAndSend() / @RabbitListener 에서 바로 쓸 수 있다.
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}