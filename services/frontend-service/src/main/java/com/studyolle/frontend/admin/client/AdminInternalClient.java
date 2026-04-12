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
 * [왜 /api/admin/** 을 호출하는가 — 다른 Internal Client 와의 차이]
 * 다른 InternalClient (AccountInternalClient 등) 는 대상 서비스의 /internal/** 경로를 직접 호출한다.
 * 그 서비스들이 frontend-service 를 ALLOWED 내부 서비스 목록에 등록해 두었기 때문이다.
 *
 * admin-service 는 /internal/** 경로를 두지 않는다. 다른 서비스가 호출할 일이 없기 때문이다.
 * 오직 /api/admin/** 만 존재한다.
 *
 * frontend-service 에서 이 경로를 호출할 때는 두 가지 선택지가 있다:
 *
 *   (A) admin-service 에 /internal/admin/** 경로를 추가로 만든다
 *   (B) 기존 /api/admin/** 을 호출하되 X-Account-Role 헤더를 수동 주입한다
 *
 * (A) 는 admin-service 의 컨트롤러를 이중화해야 하고, (B) 는 헤더 한 줄이면 해결된다.
 * 여기서는 (B) 를 택했다.
 * AdminPageController 에서 role 을 이미 알고 있으니 그대로 넘겨주면 된다.
 *
 * [anti-corruption layer 패턴]
 * 이 클래스는 네트워크 경계에서 CommonApiResponse 래퍼를 벗겨낸다.
 * 그 결과 Controller 와 Thymeleaf 템플릿은 순수한 AdminPageResponse<AdminMemberDto> 만 다루면 된다.
 * 래퍼는 네트워크 계약일 뿐 도메인 모델이 아니므로, 경계에서 변환해 두면 내부 코드가 깔끔해진다.
 * 나중에 래퍼 구조가 바뀌어도 수정할 곳이 이 클래스 한 군데뿐이다.
 */
@Component
@RequiredArgsConstructor
public class AdminInternalClient {

    private final RestTemplate restTemplate;

    // Eureka 에 등록된 admin-service 이름.
    // RestTemplate 이 @LoadBalanced 로 설정되어 있어야 이 host 이름이 실제 인스턴스로 변환된다.
    // (RestTemplateConfig 에서 이미 @LoadBalanced 가 붙어 있다고 가정)
    private static final String ADMIN_SERVICE_URL = "http://ADMIN-SERVICE";

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