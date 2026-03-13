package com.studyolle.study;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * study-service 애플리케이션 진입점.
 *
 * =============================================
 * 이 서비스의 역할
 * =============================================
 *
 * 스터디 도메인의 모든 비즈니스 로직을 담당한다:
 * - 스터디 생성, 조회, 삭제
 * - 멤버 가입(즉시/승인제), 탈퇴
 * - 스터디 설정 변경 (소개, 배너, 태그, 지역, 상태, 경로, 제목)
 * - 스터디 검색(키워드) 및 관심사 기반 추천
 * - 승인제 가입 신청 처리 (승인/거절)
 *
 * =============================================
 * 포트 및 DB
 * =============================================
 *
 * 포트: 8083
 * DB:   postgres-study (포트 5434) — study, join_request 테이블
 *
 * =============================================
 * @SpringBootApplication
 * =============================================
 *
 * 세 가지 어노테이션의 조합이다:
 * - @Configuration    : 이 클래스를 Spring 설정 클래스로 등록
 * - @EnableAutoConfiguration : classpath 의 라이브러리를 분석하여 자동 설정 적용
 *   (spring-data-jpa 가 있으면 JPA 설정, spring-web 이 있으면 MVC 설정 등)
 * - @ComponentScan    : 현재 패키지(com.studyolle.study) 하위의 @Component, @Service,
 *   @Repository, @Controller 를 스캔하여 Spring 빈으로 등록
 *
 * =============================================
 * @EnableFeignClients
 * =============================================
 *
 * @FeignClient 어노테이션이 붙은 인터페이스를 스캔하여 Feign 구현체를 자동 생성한다.
 * 이 어노테이션이 없으면 MetadataFeignClient 가 Spring 빈으로 등록되지 않아
 * @Autowired(또는 @RequiredArgsConstructor) 주입 시 NoSuchBeanDefinitionException 이 발생한다.
 *
 * =============================================
 * 모노리틱과의 핵심 차이
 * =============================================
 *
 * - JWT 라이브러리 없음: JWT 검증은 api-gateway 가 전담한다.
 * - Spring Security 최소화: anyRequest().permitAll() 로 모든 요청 허용.
 *   인증된 사용자 식별은 X-Account-Id 헤더로 처리한다.
 * - Tag/Zone 엔티티 없음: tagIds/zoneIds(Long) 만 저장.
 *   실제 태그/지역 데이터는 metadata-service 가 소유한다.
 * - ApplicationEventPublisher 없음: StudyCreatedEvent, StudyUpdateEvent 발행 코드 제거.
 *   Phase 5 에서 RabbitMQ 기반 메시지 발행으로 대체 예정이다.
 * - @EnableFeignClients 추가: metadata-service 내부 호출을 위한 Feign Client 활성화.
 */
@SpringBootApplication
@EnableFeignClients
public class StudyServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(StudyServiceApplication.class, args);
    }
}