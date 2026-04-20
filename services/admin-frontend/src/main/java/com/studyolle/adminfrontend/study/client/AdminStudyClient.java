package com.studyolle.adminfrontend.study.client;

import com.studyolle.adminfrontend.member.dto.AdminCommonApiResponse;
import com.studyolle.adminfrontend.member.dto.AdminPageResponse;
import com.studyolle.adminfrontend.study.dto.AdminStudyDto;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Optional;

/**
 * admin-frontend 가 admin-service 의 /api/admin/studies 를 호출하는 클라이언트.
 *
 * [회원 관리 AdminInternalClient 와의 차이점]
 * - 엔드포인트 경로만 다르다: /api/admin/members → /api/admin/studies
 * - 응답 DTO 가 다르다: AdminMemberDto → AdminStudyDto
 * - 강제 비공개는 요청 본문이 비어있어 HttpEntity 에 null body 를 넣는다
 * 이외의 구조는 완전히 동일하다.
 * 헤더 수동 주입, CommonApiResponse 래퍼 해제, lb:// 스킴 사용 전부 그대로다.
 * 이 대칭성이 유지되면 나중에 태그 관리·알림 관리 같은 기능을 추가할 때도 "같은 모양으로 한 번 더 짜면 된다" 는 예측이 가능해진다.
 *
 * [member 패키지 DTO 를 재사용해도 괜찮은가]
 * AdminCommonApiResponse 와 AdminPageResponse 는 제네릭이라 회원·스터디 어디에나 쓸 수 있다.
 * 이런 "도메인 중립적 인프라 DTO" 는 한 번 만들고 재사용하는 것이 낫다.
 * 다만 패키지 위치가 member 아래라 이름이 거슬릴 수도 있는데, 필요하면 common 패키지로 옮겨도 되고,
 * 학습 프로젝트 단계에서는 이대로 두어도 충분히 동작한다.
 */
@Component
@RequiredArgsConstructor
public class AdminStudyClient {

    private final RestTemplate restTemplate;
    private static final String ADMIN_SERVICE_URL = "lb://ADMIN-SERVICE";

    /*
     * 관리자 스터디 목록 조회.
     */
    public AdminPageResponse<AdminStudyDto> listStudies(
            String keyword,
            int page,
            int size,
            Long accountId,
            String nickname) {

        String url = UriComponentsBuilder
                .fromUriString(ADMIN_SERVICE_URL + "/api/admin/studies")
                .queryParam("page", page)
                .queryParam("size", size)
                .queryParamIfPresent("keyword", (keyword != null && !keyword.isBlank()) ? Optional.of(keyword) : Optional.empty())
                .toUriString();

        HttpHeaders headers = buildAdminHeaders(accountId, nickname);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<AdminCommonApiResponse<AdminPageResponse<AdminStudyDto>>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<>() {}
        );

        AdminCommonApiResponse<AdminPageResponse<AdminStudyDto>> body = response.getBody();
        if (body == null || !body.isSuccess()) {
            String msg = (body != null) ? body.getMessage() : "응답이 비어있습니다";
            throw new IllegalStateException("admin-service 스터디 목록 조회 실패: " + msg);
        }

        return body.getData();
    }

    /*
     * 스터디 강제 비공개 처리.
     *
     * 요청 본문이 없으므로 HttpEntity 에 null body 만 넘긴다.
     * Content-Type 헤더도 필요 없다 (본문이 없으니 협상할 표현이 없다).
     */
    public AdminStudyDto forceCloseStudy(
            String path,
            Long accountId,
            String nickname) {

        String url = ADMIN_SERVICE_URL + "/api/admin/studies/" + path + "/force-close";

        HttpHeaders headers = buildAdminHeaders(accountId, nickname);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<AdminCommonApiResponse<AdminStudyDto>> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                new ParameterizedTypeReference<>() {}
        );

        AdminCommonApiResponse<AdminStudyDto> result = response.getBody();
        if (result == null || !result.isSuccess()) {
            String msg = (result != null) ? result.getMessage() : "응답이 비어있습니다";
            throw new IllegalStateException("admin-service 강제 비공개 실패: " + msg);
        }
        return result.getData();
    }

    /*
     * 관리자 호출용 헤더 세트를 만든다.
     *
     * admin-service 의 AdminAuthInterceptor 는 X-Account-Role == ROLE_ADMIN 을 검사한다.
     * 이 클라이언트는 admin-gateway 를 거치지 않으므로 게이트웨이가 해주던 헤더 주입을 수동으로 흉내낸다.
     */
    private HttpHeaders buildAdminHeaders(Long accountId, String nickname) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Account-Id", String.valueOf(accountId));
        headers.set("X-Account-Nickname", nickname == null ? "" : nickname);
        headers.set("X-Account-Role", "ROLE_ADMIN");
        return headers;
    }
}