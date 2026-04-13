package com.studyolle.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 관리자 권한 검증 필터 — /api/admin/** 경로에만 적용된다.
 *
 * [동작 원리]
 * 이 필터는 JwtAuthenticationFilter 가 먼저 실행된 뒤에 돌아간다.
 * 앞선 필터가 JWT 를 검증하고 X-Account-Role 헤더를 추가해 놓으면,
 * 여기서는 그 헤더 값이 "ROLE_ADMIN" 인지만 확인한다.
 * JWT 검증 자체는 이미 끝났으므로 다시 할 필요가 없다.
 *
 * [왜 필터를 분리했는가 — 단일 책임 원칙]
 * "권한 검사" 를 JwtAuthenticationFilter 안에 집어넣어도 동작은 한다.
 * 그러나 그렇게 하면 "인증(너 누구야)" 과 "인가(이거 할 수 있어)" 가 한 클래스에 섞인다.
 * 나중에 "스터디 매니저만 접근 가능" 같은 다른 인가 규칙을 추가할 때마다
 * JwtAuthenticationFilter 를 계속 수정하게 되어 한 파일이 비대해진다.
 * 인가 규칙을 별도 필터로 떼어 두면 application.yml 에서 필요한 경로에만 조립할 수 있다.
 *
 * [순서가 중요하다]
 * application.yml 의 filters 목록에서 반드시 JwtAuthenticationFilter 가 먼저 와야 한다.
 * 순서를 바꾸면 X-Account-Role 헤더가 아직 세팅되지 않은 상태로 이 필터가 돌아
 * 모든 요청이 403 으로 차단된다.
 *
 * [401 이 아닌 403 인 이유]
 * 401 (Unauthorized) = "인증 자체가 안 됨, 너 누구냐"
 * 403 (Forbidden)    = "인증은 됐지만 이 작업을 할 권한이 없음"
 * 여기까지 도달했다는 것은 JWT 검증을 통과했다는 뜻이므로, 의미론적으로 403 이 맞다.
 */
@Component
public class AdminRoleFilter extends AbstractGatewayFilterFactory<AdminRoleFilter.Config> {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    public AdminRoleFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {

            // JwtAuthenticationFilter 가 추가해 놓은 X-Account-Role 헤더를 읽는다.
            // 이 헤더가 null 이라는 것은 둘 중 하나를 의미한다:
            //   1. 앞선 JwtAuthenticationFilter 가 돌지 않았다 (application.yml 필터 순서 문제)
            //   2. JWT 에 role claim 이 없다 (구버전 JWT 또는 발급 로직 누락)
            String role = exchange.getRequest()
                    .getHeaders()
                    .getFirst("X-Account-Role");

            // 관리자가 아니면 403 Forbidden 으로 즉시 차단
            // 여기서 setComplete() 를 호출하면 체인의 다음 필터나 실제 라우팅이 실행되지 않는다.
            if (!ROLE_ADMIN.equals(role)) {
                exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                return exchange.getResponse().setComplete();
            }

            // 관리자 확인 완료 → 다음 필터 또는 실제 라우팅으로 통과
            return chain.filter(exchange);
        };
    }

    // AbstractGatewayFilterFactory 는 필터별 설정 클래스를 요구한다.
    // 이 필터는 설정값이 필요 없으므로 빈 클래스만 둔다.
    public static class Config {}
}