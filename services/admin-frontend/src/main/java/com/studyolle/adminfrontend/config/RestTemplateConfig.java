package com.studyolle.adminfrontend.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate Bean 을 Spring 컨테이너에 등록하는 설정 클래스.
 *
 * [왜 단 한 줄의 Bean 선언을 위해 별도 클래스가 필요한가]
 * Spring Boot 는 많은 것을 자동 구성해주지만 RestTemplate 은 그 대상이 아니다.
 * 역사적인 이유가 있는데, RestTemplate 은 여러 방식으로 커스터마이즈될 수 있어서
 * Spring 팀이 "사용자가 직접 자신의 요구에 맞게 만들어 등록하라" 는 입장을 취했다.
 * 따라서 이렇게 Bean 메서드를 작성해주는 것이 표준 방식이다.
 *
 * [@LoadBalanced 가 무엇을 해주는가]
 * 이 어노테이션이 붙은 RestTemplate 은 요청을 보낼 때 URL 을 한 번 가로채서
 * "lb://SERVICE-NAME/path" 같은 논리 주소를 실제 호스트:포트로 변환한다.
 * 변환은 Spring Cloud LoadBalancer 가 Eureka 에 등록된 인스턴스 목록을 조회해서 수행한다.
 * 이 어노테이션이 없으면 lb:// 스킴을 일반 HTTP 스킴처럼 해석하려다
 * UnknownHostException 으로 실패한다.
 *
 * [왜 프론트엔드에 RestTemplate 을 쓰는가 — Feign 이 아니라]
 * 백엔드 서비스들(admin-service, study-service 등) 은 Feign Client 를 쓰지만
 * 프론트엔드 모듈은 RestTemplate 을 쓴다.
 * 이것은 프로젝트 전체의 일관성 때문이다.
 * frontend-service 가 이미 RestTemplate 으로 통일되어 있고, 학습 목적상 두 프론트엔드 모듈이 같은 스타일을 유지하는 것이 비교·이해에 도움이 된다.
 * 실무에서는 어느 쪽을  써도 무방하지만, 한 모듈 안에서 두 방식을 섞어 쓰는 것은 혼란스러우니 피해야 한다.
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    @LoadBalanced
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}