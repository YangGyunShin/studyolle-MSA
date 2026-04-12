package com.studyolle.frontend.admin.client;

import com.studyolle.frontend.admin.dto.AdminCommonApiResponse;
import com.studyolle.frontend.admin.dto.AdminMemberDto;
import com.studyolle.frontend.admin.dto.AdminPageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Optional;

/**
 * frontend-service 가 admin-service 를 호출하는 클라이언트.
 *
 * ============================================================
 * 다른 InternalClient 와 무엇이 다른가
 * ============================================================
 *
 * 이 프로젝트에는 frontend-service 가 백엔드 서비스를 호출하는 클라이언트가 여러 개 있다:
 *   AccountInternalClient       → account-service 의 /internal/accounts/**
 *   StudyInternalClient         → study-service 의 /internal/studies/**
 *   EventInternalClient         → event-service 의 /internal/events/**
 *   NotificationInternalClient  → notification-service 의 /internal/notifications/**
 *
 * 이 네 개는 모두 같은 패턴을 따른다. 그런데 AdminInternalClient 만 다섯 가지 측면에서 다르다.
 * 이 차이가 우연이 아니라 admin-service 의 구조적 특성에서 비롯된 것이므로 꼭 이해해 둬야 한다.
 *
 *
 * 차이 1. 호출하는 경로가 다르다
 * ------------------------------------------------------------
 *   다른 클라이언트  : /internal/accounts/123          (내부 전용 뒷문)
 *   AdminClient     : /api/admin/members              (공개 API 정문)
 *
 * account-service 같은 데이터 보유 서비스는 두 종류의 출입구를 가진다.
 *   /api/**       : 브라우저가 api-gateway 를 거쳐 들어오는 정문 (JWT 필요)
 *   /internal/**  : 다른 백엔드 서비스만 쓰는 뒷문 (X-Internal-Service 헤더만 필요)
 *
 * frontend-service 는 백엔드 서비스이므로 뒷문(/internal) 을 쓰는 것이 자연스럽다.
 *
 * 그런데 admin-service 는 /internal/** 경로 자체가 없다.
 * 만들지 않은 이유는 admin-service 를 호출하는 곳이 frontend-service 단 한 군데뿐이기 때문이다.
 * 한 군데를 위해 컨트롤러를 두 벌(/api/admin + /internal/admin) 만드는 것은 낭비라
 * 정문(/api/admin) 만 두고 그것을 frontend-service 가 그대로 쓰도록 했다.
 *
 *
 * 차이 2. 보내야 하는 헤더 종류가 다르다
 * ------------------------------------------------------------
 *   다른 클라이언트  : X-Internal-Service: frontend-service
 *                    (필요시 X-Account-Id 추가)
 *   AdminClient     : X-Account-Id, X-Account-Nickname, X-Account-Role
 *
 * 이유는 차이 1 에서 이어진다. 호출하는 경로가 다르므로 그 경로를 지키는 문지기도 다르다.
 *
 *   /internal/** 의 문지기  : InternalRequestFilter
 *                            "X-Internal-Service 헤더에 허용된 서비스 이름이 적혀 있는가" 검사
 *
 *   /api/admin/** 의 문지기 : AdminAuthInterceptor
 *                            "X-Account-Role 헤더가 ROLE_ADMIN 인가" 검사
 *
 * 후자를 통과하려면 X-Account-Role 헤더가 반드시 필요하다. 그래서 이 클라이언트는
 * 다른 클라이언트가 쓰지 않는 X-Account-Role 헤더를 직접 만들어서 넣는다.
 *
 *
 * 차이 3. 헤더를 자동으로 받을 수 없어 손으로 채워 넣는다
 * ------------------------------------------------------------
 * 평소 브라우저가 /api/admin/** 을 호출할 때는 api-gateway 의 JwtAuthenticationFilter 가
 * JWT 에서 role 을 꺼내서 X-Account-Role 헤더를 자동으로 붙여준다. 즉 정상적인 외부 요청은
 * 헤더 주입을 신경 쓸 필요가 없다.
 *
 * 그러나 이 클라이언트는 frontend-service 의 JVM 안에서 admin-service 를 직접 호출한다.
 * 즉 api-gateway 를 거치지 않는다. 그러면 자동 헤더 주입도 일어나지 않는다.
 * 결국 이 클라이언트가 손으로 흉내내어 헤더를 채워 넣어야 한다.
 *
 * 호출 경로 비교 :
 *
 *   [브라우저 호출 — 평소 외부 요청]
 *   브라우저 → api-gateway → admin-service
 *                  ↑
 *           여기서 자동으로 X-Account-Role 헤더 주입
 *
 *   [이 클라이언트 호출 — 내부 호출]
 *   frontend-service → admin-service
 *           ↑
 *   api-gateway 를 거치지 않으므로 자동 주입 없음
 *   → 이 클라이언트가 직접 헤더를 만들어서 넣어야 한다
 *
 *
 * 차이 4. InternalHeaderHelper 를 쓰지 않는다
 * ------------------------------------------------------------
 *   다른 클라이언트  : InternalHeaderHelper.build(accountId) 한 줄로 헤더 생성
 *   AdminClient     : new HttpHeaders() 로 직접 만든다
 *
 * InternalHeaderHelper 는 X-Internal-Service + X-Account-Id 두 개만 채워주는 헬퍼다.
 * 이 클라이언트가 필요로 하는 헤더는 X-Account-Id, X-Account-Nickname, X-Account-Role 세 개라
 * 헬퍼가 만들어주는 헤더 세트와 형태가 맞지 않는다. 그래서 헬퍼를 쓰지 않고 직접 만든다.
 *
 * 나중에 권한 변경, 스터디 관리 등 admin 호출이 두세 개로 늘어나면
 * AdminHeaderHelper 같은 전용 헬퍼를 만드는 게 좋다. 지금은 메서드가 하나뿐이라
 * 헬퍼를 만들면 오히려 추상화 비용만 든다.
 *
 *
 * 차이 5. 응답에 CommonApiResponse 래퍼가 한 겹 더 있다
 * ------------------------------------------------------------
 *   다른 클라이언트  : /internal/** 응답은 날것 (AccountSummaryDto 직접 받음)
 *   AdminClient     : /api/admin/** 응답은 CommonApiResponse 로 한 번 감싸짐
 *
 * 이것도 차이 1 의 연장선이다. /internal/** 은 서비스 간 직접 통신용이라 단순한 응답으로 충분하지만,
 * /api/admin/** 은 공개 API 규약을 따르므로 모든 응답이 { success, message, data } 구조로 감싸진다.
 * 따라서 이 클라이언트는 응답을 받은 뒤 .getData() 로 한 겹 벗겨내는 단계가 추가된다.
 *
 * 벗겨내는 작업을 Controller 가 아니라 Client 에서 하는 이유는 anti-corruption layer 패턴 때문이다.
 * 네트워크 계약(CommonApiResponse) 이 Controller 와 Thymeleaf 까지 흘러가지 않도록
 * 경계에서 차단하면, 내부 코드는 순수한 도메인 모델만 다루게 되어 깔끔해진다.
 *
 *
 * ============================================================
 * 모든 차이의 한 줄 요약
 * ============================================================
 *
 * 다른 백엔드 서비스는 "데이터를 가진 서비스" 라서 내부 호출 전용 뒷문(/internal) 을 마련해 두었다.
 * admin-service 는 "데이터를 모으는 서비스" 라 호출받는 곳이 frontend-service 한 군데뿐이고,
 * 굳이 뒷문을 만들지 않고 정문(/api/admin) 만 두었다.
 * 정문을 쓴다는 단 하나의 결정에서 위의 다섯 가지 차이가 모두 파생된다.
 */
@Component
@RequiredArgsConstructor
public class AdminInternalClient {

    private final RestTemplate restTemplate;

    // Eureka 에 등록된 admin-service 이름.
    // RestTemplate 이 @LoadBalanced 로 설정되어 있어야 이 host 이름이 실제 인스턴스로 변환된다.
    // (RestTemplateConfig 에서 이미 @LoadBalanced 가 붙어 있다고 가정)
    private static final String ADMIN_SERVICE_URL = "lb://ADMIN-SERVICE";
    /**
     * 관리자 회원 목록 조회.
     *
     * @param keyword   검색어 (null 가능)
     * @param page      페이지 번호 (0 base)
     * @param size      페이지 크기
     * @param accountId 현재 로그인한 관리자의 ID (헤더 주입용)
     * @param nickname  현재 로그인한 관리자의 닉네임 (헤더 주입용)
     * @return 래퍼가 벗겨진 순수 페이지 응답
     */
    public AdminPageResponse<AdminMemberDto> listMembers(
            String keyword,
            int page,
            int size,
            Long accountId,
            String nickname) {

        // URL 구성 — UriComponentsBuilder 가 쿼리 파라미터 인코딩까지 알아서 처리한다.
        // queryParamIfPresent 는 Optional 이 비어 있으면 파라미터 자체를 URL 에 추가하지 않는다.
        // 덕분에 keyword 가 null 이거나 빈 문자열이면 "?keyword=" 조차 붙지 않는 깔끔한 URL 이 된다.
        String url = UriComponentsBuilder
                .fromHttpUrl(ADMIN_SERVICE_URL + "/api/admin/members")
                .queryParam("page", page)
                .queryParam("size", size)
                .queryParamIfPresent("keyword",
                        (keyword != null && !keyword.isBlank())
                                ? Optional.of(keyword)
                                : Optional.empty())
                .toUriString();

        // admin-service 의 AdminAuthInterceptor 는 X-Account-Role 헤더만 검사한다.
        // 따라서 Gateway 가 자동 주입해 주는 헤더 세트를 여기서 수동으로 흉내내어 넣는다.
        // 이 호출은 Gateway 를 통하지 않고 직접 admin-service 로 나가기 때문이다.
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Account-Id", String.valueOf(accountId));
        headers.set("X-Account-Nickname", nickname == null ? "" : nickname);
        headers.set("X-Account-Role", "ROLE_ADMIN");

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        // ParameterizedTypeReference 가 필요한 이유 — 제네릭 타입 소거 회피
        // RestTemplate 은 기본적으로 제네릭 정보를 런타임에 잃어버린다.
        // TypeReference 를 익명 서브클래스로 만들면 그 서브클래스의 제네릭 정보는
        // 런타임에도 남아 있어 Jackson 이 올바른 타입으로 역직렬화할 수 있다.
        ResponseEntity<AdminCommonApiResponse<AdminPageResponse<AdminMemberDto>>> response =
                restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        entity,
                        new ParameterizedTypeReference<>() {}
                );

        AdminCommonApiResponse<AdminPageResponse<AdminMemberDto>> body = response.getBody();

        // 방어 코드 — body 가 null 이거나 success == false 인 경우
        // admin-service 의 GlobalExceptionHandler 가 500 응답을 보내면 여기서 null 이 올 수 있다.
        // success == false 는 admin-service 가 정상 응답했지만 비즈니스 로직상 실패한 경우다.
        if (body == null || !body.isSuccess()) {
            String msg = (body != null) ? body.getMessage() : "응답이 비어있습니다";
            throw new IllegalStateException("admin-service 호출 실패: " + msg);
        }

        // 래퍼를 벗겨 순수 페이지 응답만 반환한다.
        // 이 이후로는 Controller 와 View 가 CommonApiResponse 를 볼 일이 없다.
        return body.getData();
    }
}