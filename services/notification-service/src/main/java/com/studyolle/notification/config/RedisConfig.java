package com.studyolle.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 연결 및 RedisTemplate 을 설정하는 클래스.
 *
 * =============================================
 * RedisTemplate 을 직접 설정하는 이유
 * =============================================
 *
 * Spring Boot 는 RedisTemplate<Object, Object> 를 자동으로 등록하지만
 * 기본 직렬화 방식이 JdkSerializationRedisSerializer 이다.
 *
 * JdkSerializationRedisSerializer 의 문제점:
 *   - 데이터를 Java 바이너리 형식(\xac\xed\x00\x05...)으로 저장한다.
 *   - Redis CLI 에서 GET notification:unread:123 을 입력하면
 *     "\xac\xed\x00\x05t\x00\x015" 같은 알아볼 수 없는 값이 나온다.
 *   - INCR / DECR 같은 Redis 숫자 연산 명령어가 동작하지 않는다.
 *     (바이너리로 저장된 "5" 는 Redis 가 숫자로 인식하지 못함)
 *
 * StringRedisSerializer 로 교체하면:
 *   - 데이터가 사람이 읽을 수 있는 문자열로 저장된다.
 *     GET notification:unread:123 → "5"
 *   - INCR / DECR 명령어가 정상 동작한다.
 *     (알림 카운터 증가/감소에 필수)
 *   - Redis CLI / RedisInsight 같은 GUI 도구에서 직접 확인 가능해 디버깅이 쉽다.
 *
 * =============================================
 * 이 서비스에서 저장하는 Redis 데이터
 * =============================================
 *
 * Key 패턴                          Value   용도
 * notification:unread:{accountId}   "5"     읽지 않은 알림 수 카운터 (INCR/DECR)
 * notification:dedup:{eventKey}     "1"     중복 이벤트 처리 방지 (SETNX + TTL 1일)
 *
 * 모두 단순 문자열/숫자이므로 StringRedisSerializer 로 충분하다.
 */
@Configuration
public class RedisConfig {

    /**
     * Key, Value 모두 String 으로 직렬화하는 RedisTemplate Bean 을 등록한다.
     *
     * 제네릭 타입을 RedisTemplate<String, String> 으로 지정하면
     * 서비스 코드에서 타입 캐스팅 없이 바로 String 값을 읽고 쓸 수 있다.
     *
     * setKeySerializer / setValueSerializer:
     *   일반 Key-Value 저장에 사용하는 직렬화 방식.
     *   increment("notification:unread:123") 처럼 단일 값 조작 시 적용.
     *
     * setHashKeySerializer / setHashValueSerializer:
     *   Redis Hash 자료구조(opsForHash)에 사용하는 직렬화 방식.
     *   현재는 Hash 를 사용하지 않지만, 나중을 위해 함께 설정한다.
     *
     * @param factory Spring 이 application.yml 의 redis.host / port 설정으로
     *                자동 생성하는 Redis 연결 팩토리
     */
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        return template;
    }
}
