package com.studyolle.admingateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AdminGatewayApplication
 *
 * 관리자 전용 게이트웨이의 진입점. 포트 9080 에서 동작한다.
 *
 * [왜 일반 사용자 게이트웨이(api-gateway) 와 분리했는가]
 * 기술적으로는 api-gateway 하나에 /api/admin/** 라우트를 추가해도 동작한다.
 * 하지만 두 가지 이유로 물리적으로 분리했다.
 *
 * 1. 공격 표면 축소
 *    일반 사용자 트래픽이 몰리는 포트와 관리자 기능이 같은 문을 공유하면,
 *    일반 사용자 측 필터 로직에 버그가 생겼을 때 관리자 경로까지 영향을 받는다.
 *    게이트웨이를 물리적으로 분리하면 일반 사용자 게이트웨이가 완전히 망가져도
 *    관리자는 9080 포트로 복구 작업을 수행할 수 있다.
 *
 * 2. 배포/운영 독립성
 *    관리자 기능은 사용자 수가 적고 변경 빈도가 낮으므로 배포 주기가 느리다.
 *    일반 사용자 게이트웨이가 새 버전으로 배포되는 동안 관리자 게이트웨이를
 *    손대지 않을 수 있어야 한다.
 *
 * [@SpringBootApplication 하나만으로 충분한 이유]
 * Spring Cloud Gateway 는 auto-configuration 이 모든 설정을 해주므로
 * @EnableGateway 같은 별도 어노테이션이 필요하지 않다. classpath 에
 * spring-cloud-starter-gateway 가 있기만 하면 자동으로 게이트웨이로 동작한다.
 */
@SpringBootApplication
public class AdminGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminGatewayApplication.class, args);
    }
}