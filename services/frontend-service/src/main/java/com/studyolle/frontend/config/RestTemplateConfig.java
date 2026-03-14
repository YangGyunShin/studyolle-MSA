package com.studyolle.frontend.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * LoadBalanced RestTemplate 설정.
 *
 * @LoadBalanced 를 붙이면 RestTemplate 이 lb://SERVICE-NAME 형식의 URL 을
 * Spring Cloud LoadBalancer + Eureka 를 통해 실제 호스트:포트로 자동 변환한다.
 *
 * 예) lb://STUDY-SERVICE/internal/studies/my-study
 *  -> http://192.168.0.10:8083/internal/studies/my-study  (Eureka 가 반환한 인스턴스)
 *
 * 이 방식으로 frontend-service -> study-service 직접 호출 (api-gateway 우회).
 * 내부 서비스 간 통신이므로 JWT 없이 X-Internal-Service 헤더만 첨부한다.
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    @LoadBalanced
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}