package com.studyolle.adminfrontend.member.client;

import com.studyolle.adminfrontend.member.dto.AdminCommonApiResponse;
import com.studyolle.adminfrontend.member.dto.AdminMemberDto;
import com.studyolle.adminfrontend.member.dto.AdminPageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.Optional;

/**
 * admin-frontend 가 admin-service 를 호출하는 클라이언트.
 *
 * [호출 경로]
 * admin-frontend (in-process)
 *   → RestTemplate (with @LoadBalanced)
 *     → Eureka lookup: ADMIN-SERVICE
 *       → admin-service /api/admin/members
 *         → AdminAuthInterceptor (X-Account-Role == ROLE_ADMIN 확인)
 *           → AdminMemberController
 *             → AccountAdminClient (Feign)
 *               → account-service /internal/accounts
 *
 * [다른 InternalClient 와의 차이점]
 *
 * 이 프로젝트에는 frontend 가 백엔드 서비스를 호출하는 클라이언트가 여러 개 있다.
 * 네 개의 일반 클라이언트(Account, Study, Event, Notification) 는 전부 /internal/** 경로를 호출하는 데 반해
 * 이 AdminClient 만 /api/admin/** 정문을 호출한다.
 * 그 결과로 다섯 가지 측면에서 구조적 차이가 생긴다.
 *
 * 차이 1 — 호출 경로:
 *   일반: /internal/accounts/123 (내부 전용 뒷문)
 *   Admin: /api/admin/members    (공개 API 정문)
 *
 * 차이 2 — 헤더 구성:
 *   일반: X-Internal-Service 헤더만 (+ 필요시 X-Account-Id)
 *   Admin: X-Account-Id, X-Account-Nickname, X-Account-Role 세 개 모두
 *
 * 차이 3 — 헤더 자동 주입 불가:
 *   평소 브라우저가 /api/admin/** 을 호출할 때는 admin-gateway 의 JwtAuthenticationFilter 가 X-Account-Role 을 자동으로 붙여준다.
 *   그러나 이 클라이언트는 admin-frontend 의 JVM 안에서 직접 호출하므로 게이트웨이를 거치지 않는다.
 *   결국 이 클라이언트가 손으로 헤더를 만들어 넣는다.
 *
 * 차이 4 — InternalHeaderHelper 미사용:
 *   그 헬퍼는 X-Internal-Service + X-Account-Id 용이라 Admin 호출 헤더 세트와 맞지 않는다.
 *
 * 차이 5 — 응답 래퍼 한 겹:
 *   /internal/** 응답은 날것으로 내려오지만 /api/admin/** 응답은 CommonApiResponse 로 감싸져 있다.
 *   이 클라이언트가 .getData() 로 벗겨내어 Controller 에는 순수 DTO 만 넘긴다.
 *   이것이 anti-corruption layer 패턴이다 — 네트워크 계약을 경계에서 차단하면 내부 코드가 깔끔해진다.
 *
 * 모든 차이의 근본 원인은 "admin-service 가 데이터 보유 서비스가 아니라 orchestration 서비스라
 * 뒷문(/internal) 을 만들지 않고 정문만 두었다" 라는 단 하나의 설계 결정에서 비롯된다.
 */
@Component
@RequiredArgsConstructor
public class AdminInternalClient {

    private final RestTemplate restTemplate;

    // Eureka 에 등록된 admin-service 의 논리 주소.
    // RestTemplate 에 @LoadBalanced 가 붙어 있어야 이 문자열이 실제 인스턴스 주소로 변환된다.
    private static final String ADMIN_SERVICE_URL = "lb://ADMIN-SERVICE";

    /**
     * 관리자 회원 목록 조회.
     *
     * @param keyword   검색어 (null 이거나 빈 문자열이면 전체 조회)
     * @param page      0-base 페이지 번호
     * @param size      페이지 크기
     * @param accountId 현재 로그인한 관리자의 id (헤더 주입용)
     * @param nickname  현재 로그인한 관리자의 닉네임 (헤더 주입용)
     * @return CommonApiResponse 래퍼를 벗겨낸 순수 페이지 응답
     */
    public AdminPageResponse<AdminMemberDto> listMembers(
            String keyword,
            int page,
            int size,
            Long accountId,
            String nickname) {

        // URL 구성.
        // queryParamIfPresent 는 Optional 이 비어있으면 파라미터 자체를 URL 에 추가하지 않아서
        // "?keyword=" 같은 빈 값도 생기지 않는 깔끔한 URL 을 만든다.
        String url = UriComponentsBuilder
                .fromUriString(ADMIN_SERVICE_URL + "/api/admin/members")
                .queryParam("page", page)
                .queryParam("size", size)
                .queryParamIfPresent("keyword", (keyword != null && !keyword.isBlank()) ? Optional.of(keyword) : Optional.empty())
                .toUriString();

        // admin-service 의 AdminAuthInterceptor 가 X-Account-Role 헤더를 검사하므로
        // 여기서 게이트웨이가 자동 주입해 주는 헤더 세트를 수동으로 흉내내어 넣는다.
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Account-Id", String.valueOf(accountId));
        headers.set("X-Account-Nickname", nickname == null ? "" : nickname);
        headers.set("X-Account-Role", "ROLE_ADMIN");

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        // ParameterizedTypeReference — 제네릭 타입 정보를 런타임까지 유지하기 위한 우회책.
        // 익명 서브클래스로 생성하면 그 서브클래스에는 제네릭 정보가 남아 있어 Jackson 이 정확한 타입으로 역직렬화할 수 있다.
        // RestTemplate 의 일반 getForObject 는 제네릭 타입을 처리하지 못해 여기서는 사용할 수 없다.
        ResponseEntity<AdminCommonApiResponse<AdminPageResponse<AdminMemberDto>>> response =
                restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        entity,
                        new ParameterizedTypeReference<>() {}
                );

        AdminCommonApiResponse<AdminPageResponse<AdminMemberDto>> body = response.getBody();

        // 방어 코드 — body 자체가 null 이거나 success==false 인 경우를 걸러낸다.
        if (body == null || !body.isSuccess()) {
            String msg = (body != null) ? body.getMessage() : "응답이 비어있습니다";
            throw new IllegalStateException("admin-service 호출 실패: " + msg);
        }

        // 래퍼를 벗겨 순수 페이지 응답만 반환 — Controller 는 CommonApiResponse 를 볼 일이 없다.
        return body.getData();
    }

    /**
     * 회원 권한 변경 — admin-service 의 PATCH /api/admin/members/{id}/role 호출.
     * <p>
     * 게이트웨이를 거치지 않으므로 X-Account-* 헤더를 직접 주입한다.
     * Content-Type: application/json 헤더도 명시해야 admin-service 가
     *
     * @RequestBody 를 정상적으로 역직렬화한다.
     */
    public AdminMemberDto updateMemberRole(
            Long memberId,
            String newRole,
            Long accountId,
            String nickname) {

        String url = ADMIN_SERVICE_URL + "/api/admin/members/" + memberId + "/role";

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Account-Id", String.valueOf(accountId));
        headers.set("X-Account-Nickname", nickname == null ? "" : nickname);
        headers.set("X-Account-Role", "ROLE_ADMIN");
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 본문은 record 가 따로 없으므로 Map 으로 전달해도 충분하다.
        // admin-service 의 RoleUpdateRequest record 와 필드명만 맞으면 역직렬화된다.
        Map<String, String> body = Map.of("role", newRole);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<AdminCommonApiResponse<AdminMemberDto>> response = restTemplate.exchange(
                url,
                HttpMethod.PATCH,
                entity,
                new ParameterizedTypeReference<>() {
                }
        );

        AdminCommonApiResponse<AdminMemberDto> result = response.getBody();
        if (result == null || !result.isSuccess()) {
            String msg = (result != null) ? result.getMessage() : "응답이 비어있습니다";
            throw new IllegalStateException("admin-service 권한 변경 실패: " + msg);
        }
        return result.getData();
    }
}