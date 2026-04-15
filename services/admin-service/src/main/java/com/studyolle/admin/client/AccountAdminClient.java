package com.studyolle.admin.client;

import com.studyolle.admin.client.dto.AccountAdminDto;
import com.studyolle.admin.client.dto.PageResponse;
import com.studyolle.admin.dto.request.RoleUpdateRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * account-service 의 /internal/** 경로를 호출하는 Feign Client.
 *
 * [Feign Client 의 마법]
 * 이 파일은 인터페이스일 뿐, 구현체가 없다. @EnableFeignClients 를 만난 Spring 이
 * 런타임에 프록시 클래스를 자동 생성한다. 이 프록시는 메서드 시그니처의 어노테이션을 읽어
 * HTTP 요청으로 변환하고, 응답을 지정된 타입으로 역직렬화한다.
 *
 * 즉 개발자는 "이 URL 에 이런 파라미터를 보내면 이런 응답이 온다" 는 "계약" 만 선언하고,
 * HTTP 클라이언트 코드는 한 줄도 쓰지 않는다. RestTemplate 을 직접 쓰는 것과 비교하면
 * 매우 간결하다.
 *
 * [name = "account-service" 가 의미하는 것]
 * 이 이름은 Eureka 에 등록된 서비스 이름이다. Feign 내부의 LoadBalancer 가 이 이름을
 * 실제 인스턴스의 host:port 로 변환한다. 덕분에 코드에 localhost:8081 같은
 * 하드코딩된 주소가 등장하지 않는다. account-service 가 여러 인스턴스로 수평 확장되어도
 * 이 코드는 전혀 수정할 필요가 없다.
 *
 * [X-Internal-Service 헤더를 메서드 파라미터로 받는 이유]
 * Feign 의 RequestInterceptor 를 써서 모든 요청에 자동 주입할 수도 있다. 그러나 이
 * 프로젝트의 다른 Feign Client (study-service 의 EventFeignClient, event-service 의
 * StudyFeignClient 등) 모두 파라미터 방식을 택했다. 일관성 유지가 중요하고, 호출 지점에서
 * 어느 서비스 이름으로 나가는지 코드만 봐도 드러난다는 장점도 있다.
 */
@FeignClient(name = "account-service")
public interface AccountAdminClient {

    /**
     * 관리자용 회원 목록 조회.
     *
     * 메서드 시그니처가 그대로 HTTP 요청으로 변환된다:
     *   GET http://account-service/internal/accounts?keyword={keyword}&page={page}&size={size}
     *   Header: X-Internal-Service: admin-service
     *
     * 반환 타입 PageResponse<AccountAdminDto> 는 Jackson 이 JSON 을 역직렬화할 때 사용한다.
     *
     * @param keyword     검색어 (null 가능, 전체 조회 시 null)
     * @param page        0 부터 시작하는 페이지 번호
     * @param size        페이지당 항목 수
     * @param serviceName X-Internal-Service 헤더 값 (보통 "admin-service")
     */
    @GetMapping("/internal/accounts")
    PageResponse<AccountAdminDto> listAccounts (
            @RequestParam(required = false) String keyword,
            @RequestParam int page,
            @RequestParam int size,
            @RequestHeader("X-Internal-Service") String serviceName);

    /**
     * 회원 권한 변경 — account-service 의 PATCH /internal/accounts/{id}/role 호출.
     *
     * @param id          권한을 변경할 대상 회원의 id
     * @param request     { "role": "ROLE_ADMIN" } 형태의 본문
     * @param serviceName X-Internal-Service 헤더 (보통 "admin-service")
     * @param requesterId X-Account-Id 헤더 — account-service 가 "자기 자신 권한 변경 금지" 검증에 사용
     */
    @PatchMapping("/internal/accounts/{id}/role")
    AccountAdminDto updateRole (
            @PathVariable("id") Long id,
            @RequestBody RoleUpdateRequest request,
            @RequestHeader("X-Internal-Service") String serviceName,
            @RequestHeader("X-Account-Id") Long requesterId);
}