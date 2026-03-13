package com.studyolle.study;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * study-service 진입점
 *
 * [역할]
 * - 스터디 생성 / 조회 / 멤버 관리 / 설정 변경
 * - 스터디 검색 및 관심사 기반 추천
 * - 승인제 가입 신청 처리
 *
 * [포트] 8083
 * [DB]   postgres-study (5434)
 *
 * [모노리틱과의 핵심 차이]
 * - JWT 라이브러리 없음: JWT 검증은 api-gateway 전담
 * - @CurrentUser 없음: X-Account-Id 헤더로 사용자 식별
 * - Tag/Zone 엔티티 없음(길 1): tagIds/zoneIds(Long) 만 저장, 실데이터는 metadata-service 소유
 * - StudyEventListener 없음: Phase 5 RabbitMQ로 대체 예정
 * - @EnableFeignClients: metadata-service 내부 호출용
 */
@SpringBootApplication
@EnableFeignClients
public class StudyServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(StudyServiceApplication.class, args);
    }
}