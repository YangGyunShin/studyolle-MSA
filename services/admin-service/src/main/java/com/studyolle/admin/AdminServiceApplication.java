package com.studyolle.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * admin-service - 플랫폼 관리자 전용 서비스
 *
 * [역할]
 * 회원, 스터디, 태그, 지역 등 플랫폼 전반의 관리 기능을 제공한다.
 *
 * [설계 원칙]
 * 1. 자체 DB 를 가지지 않는다.
 *    각 서비스(account, study, event, notification)의 /internal/** 엔드포인트를
 *    Feign Client 로 호출하여 데이터를 집계한다.
 *    이렇게 하면 데이터 소유권(ownership)이 명확하게 유지된다.
 *
 * 2. 이중 권한 검증을 수행한다.
 *    - 1차: api-gateway 의 AdminRoleFilter 에서 X-Account-Role == ROLE_ADMIN 확인
 *    - 2차: admin-service 내부의 SecurityConfig / Interceptor 에서 한 번 더 검증
 *    Gateway 가 유일한 방어선이 되면 Gateway 우회 시 완전히 무방비해지므로,
 *    내부 네트워크에서의 비정상 접근도 차단하기 위함이다.
 *
 * 3. /internal/** 경로는 노출하지 않는다.
 *    admin-service 는 다른 서비스로부터 호출받는 쪽이 아니라,
 *    다른 서비스를 호출하는 쪽 (consumer / orchestrator) 이다.
 *
 * [포트] 8082
 */
@SpringBootApplication
@EnableDiscoveryClient  // Eureka 에 admin-service 로 등록 → lb://ADMIN-SERVICE 로 발견 가능
@EnableFeignClients     // @FeignClient 어노테이션이 붙은 인터페이스를 스캔하여 Bean 으로 등록
public class AdminServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminServiceApplication.class, args);
    }
}