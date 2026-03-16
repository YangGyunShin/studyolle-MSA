package com.studyolle.frontend.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * @LoadBalanced RestTemplate 빈(Bean) 등록.
 *
 * ====================================================================
 * [@LoadBalanced 에지노테이션이 하는 일]
 * ====================================================================
 *
 * @LoadBalanced 를 등록하면 Spring 이 RestTemplate 인터셈터(Interceptor)에
 * LoadBalancerInterceptor 를 자동으로 등록한다.
 * 이 인터셈터가 "lb://" 로 시작하는 URL 을 감지하면 다음 흐름을 실행한다.
 *
 *   RestTemplate.exchange("lb://STUDY-SERVICE/internal/...", ...)
 *         |
 *         | LoadBalancerInterceptor 감지
 *         v
 *   SpringCloudLoadBalancer → EurekaDiscoveryClient → Eureka Server(:8761)
 *         |                        "STUDY-SERVICE 의 주소 목록 주세요"
 *         | Eureka 응답: [{ip:"10.0.0.5", port:8083}]
 *         v
 *   RoundRobinLoadBalancer 가 인스턴스 하나 선택
 *         |
 *         | URL 변환:
 *         | "lb://STUDY-SERVICE/internal/studies/my-study/page-data?accountId=123"
 *         |  → "http://10.0.0.5:8083/internal/studies/my-study/page-data?accountId=123"
 *         v
 *   실제 HTTP GET 요청 전송
 *
 * @LoadBalanced 없이 lb:// URL 을 쓰면 UnknownHostException 이 발생한다.
 * ("lb" 라는 호스트 이름을 DNS 에서 찾으려 하면 실패하는 리유)
 *
 * ====================================================================
 * [이 빈이 없으면 덕 간단하지 않나?]
 * ====================================================================
 *
 * application.yml 에 study-service 주소를 직접 적었다면 안 될까?
 *   app.study-service-base-url: http://localhost:8083
 *
 * 이 방식은 개발환경에서만 동작한다.
 * Docker/운영 환경에서는 study-service 의 IP 가 매번 다를 수 있고
 * 인스턴스가 여러 개 떠 있을 수도 있다. lb:// + Eureka 조합은
 * 주소가 바뀔어도 자동으로 추적하고 여러 인스턴스 간
 * 트래픽을 분산시켜주는 이점이 있다.
 *
 * ====================================================================
 * [이 빈을 주입받는 곳]
 * ====================================================================
 *
 * StudyInternalClient   ← @Component, 생성자 주입
 * AccountInternalClient ← @Component, 생성자 주입
 *
 * 주의: @LoadBalanced RestTemplate 빈이 여러 개일 경우
 *        @LoadBalanced @Qualifier("lbRestTemplate") 로 구분해야 한다.
 *        지금은 하나이므로 @Qualifier 는 생략한다.
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    @LoadBalanced
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
/*
 * ====================================================================
 * [restTemplate.exchange() 이후 study-service 내부 전체 흐름]
 *
 * restTemplate.exchange() 는 결국 HTTP 요청을 하나 보내는 것이다.
 * 받는 쪽(study-service)은 그게 브라우저에서 온 요청인지, 다른 서비스에서 온 요청인지 구분하지 않는다.
 * HTTP 요청이 들어온 것이므로 자기 서버의 처리 흐름을 똑같이 밟는다.
 *
 * restTemplate.exchange("lb://STUDY-SERVICE/internal/studies/{path}/page-data", ...)
 *         |
 *         | HTTP GET 전송 (LoadBalancer 가 lb:// 를 실제 IP:PORT 로 변환 후 전송)
 *         v
 * [study-service 내부]
 *
 *   DispatcherServlet
 *         |  HTTP 요청 수신.
 *         |  URL 패턴을 보고 어느 컨트롤러 메서드로 보낼지 결정한다.
 *         v
 *   InternalRequestFilter        (HandlerInterceptor)
 *         |  "/internal/**" 경로 감지.
 *         |  X-Internal-Service 헤더 검사.
 *         |    - 헤더 없음           → 403 반환 (Controller 까지 도달하지 않음)
 *         |    - "frontend-service"  → 통과
 *         v
 *   InternalStudyController      (@RestController)
 *         |  @GetMapping("/internal/studies/{path}/page-data") 메서드 실행.
 *         |  @PathVariable, @RequestParam 으로 파라미터 바인딩.
 *         |  비즈니스 처리를 StudyService 에 위임한다.
 *         v
 *   StudyService                 (@Service)
 *         |  isManager / isMember / hasPendingRequest 계산 등 비즈니스 로직 실행.
 *         |  DB 조회가 필요한 경우 StudyRepository 에 위임한다.
 *         v
 *   StudyRepository              (@Repository, Spring Data JPA)
 *         |  JPQL 또는 QueryDSL 쿼리를 생성해 DB 에 전달한다.
 *         v
 *   PostgreSQL                   (실제 데이터 저장소)
 *         |  쿼리 실행 후 결과 반환.
 *         v
 *   StudyRepository → StudyService
 *         |  Study 엔티티를 받아 StudyPageDataDto 로 조립한다.
 *         v
 *   InternalStudyController
 *         |  @RestController + Jackson 이 StudyPageDataDto 를 JSON 으로 직렬화.
 *         |  HTTP 응답 바디에 담아 반환.
 *         v
 * [study-service 외부]
 *
 *   restTemplate.exchange() 응답 수신
 *         |  MappingJackson2HttpMessageConverter 가
 *         |  JSON → StudyPageDataDto 로 역직렬화.
 *         v
 *   response.getBody()
 *         |  StudyPageDataDto 반환.
 *         v
 *   StudyPageController
 *         |  model 에 study, isManager, isMember 등을 담는다.
 *         v
 *   Thymeleaf → 완성된 HTML → 브라우저
 *
 * ====================================================================
 * [핵심 정리]
 *
 * restTemplate.exchange() 는 단지 HTTP 요청을 보내는 도구다.
 * 받는 서비스는 자신이 독립적인 서버이므로,
 * 호출하는 쪽이 브라우저든 다른 서비스의 RestTemplate 이든
 * 내부 처리 흐름(DispatcherServlet → Filter → Controller → Service → Repository) 을 동일하게 밟는다.
 *
 * 단, InternalRequestFilter 가 "/internal/**" 경로에 대해 X-Internal-Service 헤더를 추가로 검사하는 것만 다르다.
 * 이를 통해 내부 서비스끼리만 통신 가능한 전용 경로를 보호한다.
 * ====================================================================
 */