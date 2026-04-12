package com.studyolle.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 관리자 권한 검증 필터 - /api/admin/** 경로 전용
 * <p>
 * [동작 원리]
 * 이 필터는 JwtAuthenticationFilter 바로 다음에 실행된다.
 * JwtAuthenticationFilter 가 JWT 를 검증하고 X-Account-Role 헤더를 추가해 놓으면,
 * 이 필터는 그 헤더 값이 "ROLE_ADMIN" 인지만 확인한다.
 * <p>
 * JWT 검증 자체는 이전 필터에서 이미 완료되었으므로 여기서는 다시 할 필요가 없다.
 * <p>
 * [왜 필터 분리인가]
 * "권한 체크" 를 JwtAuthenticationFilter 안에 녹여도 동작은 한다.
 * 하지만 그렇게 하면 "인증(너 누구야)" 과 "인가(이거 할 수 있어)" 가 한 클래스에 섞여
 * 단일 책임 원칙을 깨뜨린다. 또한 관리자 경로 외의 다른 경로(예: 스터디 매니저만 접근 가능)
 * 를 나중에 추가할 때 매번 JwtAuthenticationFilter 를 수정하게 된다.
 * <p>
 * 필터를 분리해 두면 application.yml 에서 필요한 경로에만 조립해서 사용할 수 있다.
 * filters:
 * - JwtAuthenticationFilter   ← 모든 인증 필요 경로에 공통
 * - AdminRoleFilter           ← 관리자 경로에만 추가
 * <p>
 * [선행 조건]
 * application.yml 의 filters 목록에서 반드시 JwtAuthenticationFilter 가 먼저 와야 한다.
 * 순서가 바뀌면 X-Account-Role 헤더가 아직 세팅되지 않은 상태로 이 필터가 돌아서
 * 모든 요청이 403 으로 차단된다.
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

            // JwtAuthenticationFilter 가 추가해 놓은 X-Account-Role 헤더 읽기
            // 이 헤더가 null 이라는 것은 이전 필터가 돌지 않았거나 JWT 에 role claim 이 없다는 뜻
            String role = exchange.getRequest()
                    .getHeaders()
                    .getFirst("X-Account-Role");

            // 관리자가 아니면 403 Forbidden
            // 401 이 아닌 이유: 401 은 "인증 자체가 안 됨", 403 은 "인증됐지만 권한 부족"
            // 여기까지 왔다는 것은 JWT 검증은 통과했다는 뜻이므로 403 이 의미적으로 맞다
            if (!ROLE_ADMIN.equals(role)) {
                exchange.getResponse()
                        .setStatusCode(HttpStatus.FORBIDDEN);
                return exchange.getResponse().setComplete();
            }

            // 관리자 확인 완료 → 다음 필터 체인으로 통과
            return chain.filter(exchange);
        };
    }

    public static class Config {}
}