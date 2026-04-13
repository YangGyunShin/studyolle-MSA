package com.studyolle.adminfrontend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AdminFrontendApplication — 관리자 전용 Thymeleaf 프론트엔드의 진입점.
 *
 * [이 모듈의 책임]
 * 관리자가 브라우저로 볼 HTML 페이지를 서빙한다.
 * DB 도 비즈니스 로직도 없으며, 오직 템플릿 렌더링과 백엔드 서비스 호출 위임만 담당한다.
 *
 * [frontend-service 와 무엇이 같고 무엇이 다른가]
 * 기술 스택(Spring MVC + Thymeleaf + Eureka Client + RestTemplate) 은 완전히 동일하다.
 * 단지 타깃 사용자가 다르고, 렌더링하는 템플릿이 다르고, 물리적 포트가 다를 뿐이다.
 * 코드베이스를 완전히 분리한 이유는 역할 분리(관리자 UI 는 관리자에게만)와 배포 독립성(일반 사용자 UI 변경이 관리자 UI 배포를 막지 않도록) 때문이다.
 *
 * [@EnableDiscoveryClient 가 없어도 동작하는 이유]
 * 과거 Spring Cloud 에서는 이 어노테이션이 필수였으나,
 * 현재는 classpath 에 eureka-client starter 가 존재하면 auto-configuration 이 자동으로 활성화하므로
 * 명시적으로 선언할 필요가 없다.
 * frontend-service 도 같은 구성이다.
 */
@SpringBootApplication
public class AdminFrontendApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminFrontendApplication.class, args);
    }
}