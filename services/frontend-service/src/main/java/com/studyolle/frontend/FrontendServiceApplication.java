package com.studyolle.frontend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * FrontendServiceApplication
 *
 * 역할: HTML 페이지를 서빙하는 프론트엔드 서비스의 진입점.
 *
 * [모노리틱과의 차이]
 * - 모노리틱: Spring Security, JPA, JWT 등 모든 기능이 하나의 애플리케이션에 있었다.
 * - MSA:      이 서비스는 HTML 파일만 내려준다.
 *             DB 연결, 비즈니스 로직, JWT 처리 모두 없다.
 *             모든 API 호출은 브라우저 JS 가 api-gateway(:8080) 에 직접 한다.
 */
@SpringBootApplication
public class FrontendServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FrontendServiceApplication.class, args);
    }
}
