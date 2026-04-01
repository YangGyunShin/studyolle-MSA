package com.studyolle.frontend.account.client;

import com.studyolle.frontend.account.dto.AccountSettingsDto;
import com.studyolle.frontend.account.dto.AccountSummaryDto;
import com.studyolle.frontend.common.InternalHeaderHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

@Component
public class AccountInternalClient {

    private final RestTemplate restTemplate;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Value("${app.account-service-base-url:lb://ACCOUNT-SERVICE}")
    private String accountServiceBaseUrl;

    public AccountInternalClient(RestTemplate restTemplate, ApplicationEventPublisher applicationEventPublisher) {
        this.restTemplate = restTemplate;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    /**
     * 네비게이션 바와 대시보드 프로필 완성도 표시에 필요한 계정 요약 정보를 가져온다.
     *
     * account-service: GET /internal/accounts/{id}
     *
     * @param accountId api-gateway 가 X-Account-Id 헤더로 전달한 사용자 DB PK.
     *                  null 이면 비로그인 상태이므로 즉시 null 을 반환한다.
     * @return AccountSummaryDto, 계정이 존재하지 않으면 null
     */
    public AccountSummaryDto getAccountSummary(Long accountId) {
        if (accountId == null) return null;

        String url = accountServiceBaseUrl + "/internal/accounts/" + accountId;
        try {
            ResponseEntity<AccountSummaryDto> response = restTemplate.exchange(
                    url, HttpMethod.GET,
                    InternalHeaderHelper.build(accountId),
                    AccountSummaryDto.class
            );
            return response.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            // 계정이 DB 에 없는 경우 (탈퇴 등). null 반환으로 nav 바를 비로그인 상태로 표시.
            return null;
        }
    }

    public AccountSettingsDto getAccountSettings(Long accountId) {
        if (accountId == null) {
            return null;
        }

        String url = accountServiceBaseUrl + "/internal/accounts/" + accountId + "/full";
        try {
            ResponseEntity<AccountSettingsDto> response = restTemplate.exchange(
                    url, HttpMethod.GET,
                    InternalHeaderHelper.build(accountId),
                    AccountSettingsDto.class
            );
            return response.getBody();
        } catch (HttpClientErrorException e) {
            return null;
        }
    }

    public List<String> getAccountTags(Long accountId) {
        if (accountId == null) {
            return Collections.emptyList();
        }
        String url = accountServiceBaseUrl + "/internal/accounts/" + accountId + "/tags";
        try {
            ResponseEntity<List<String>> response = restTemplate.exchange(
                    url, HttpMethod.GET,
                    InternalHeaderHelper.build(accountId),
                    new ParameterizedTypeReference<List<String>>() {}
            );
            return response.getBody() != null ? response.getBody() : Collections.emptyList();
        } catch (HttpClientErrorException e) {
            return Collections.emptyList();
        }
    }

    public List<String> getAccountZones(Long accountId) {
        if (accountId == null) {
            return Collections.emptyList();
        }
        String url = accountServiceBaseUrl + "/internal/accounts/" + accountId + "/zones";
        try {
            ResponseEntity<List<String>> response = restTemplate.exchange(
                    url, HttpMethod.GET,
                    InternalHeaderHelper.build(accountId),
                    new ParameterizedTypeReference<List<String>>() {
                    }
            );
            return response.getBody() != null ? response.getBody() : Collections.emptyList();
        } catch (HttpClientErrorException e) {
            return Collections.emptyList();
        }
    }

    public AccountSummaryDto getAccountByNickname(String nickname) {
        String url = accountServiceBaseUrl + "/internal/accounts/by-nickname/" + nickname;
        try {
            ResponseEntity<AccountSummaryDto> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    InternalHeaderHelper.build(null),
                    AccountSummaryDto.class
            );
            return response.getBody();
        } catch (HttpClientErrorException e) {
            return null;
        }
    }
    /*
     * ====================================================================
     * [설계 해설] AccountInternalClient 는 왜 이렇게 간단한가
     * ====================================================================
     *
     * 1. 이 클라이언트의 역할이 작은 이유
     * --------------------------------------------------------------------
     * frontend-service 의 관점에서 account-service 와 대화해야 하는 상황은
     * 사실 많지 않다. 이유는 아래와 같다.
     *
     * 로그인, 회원가입, 비밀번호 변경 같은 "쓰기(Write)" 작업은
     * 브라우저의 JS fetch 가 api-gateway 를 통해 account-service 에 직접 보낸다.
     * frontend-service 는 이 흐름에 관여하지 않는다.
     * (AuthPageController 가 왜 클라이언트 없이 단순했는지와 같은 이유)
     *
     * 반면 "읽기(Read)" — 현재 로그인한 사용자가 누구인지, 프로필이 어떻게 생겼는지 —
     * 는 Thymeleaf 로 HTML 을 렌더링하기 전에 미리 알아야 한다.
     * 이 "미리 알아야 하는 읽기 작업"만이 이 클라이언트의 역할이다.
     *
     * 2. StudyInternalClient 와 비교
     * --------------------------------------------------------------------
     * StudyInternalClient 는 메서드가 많다.
     * 왜냐하면 스터디 상세, 설정, 가입 관리 등 페이지마다 서버에서
     * 초기 데이터를 가져와야 하는 경우가 많기 때문이다.
     *
     * AccountInternalClient 는 메서드가 적다.
     * 왜냐하면 "현재 로그인한 사용자 정보"라는 하나의 질문으로
     * 거의 모든 페이지의 요구사항이 충족되기 때문이다.
     * (nav 바 닉네임 표시, 프로필 완성도 계산 모두 이 하나의 DTO 로 처리)
     *
     * 이처럼 클라이언트의 크기는 "해당 서비스와 얼마나 자주, 다양하게
     * 대화해야 하는가"에 비례한다. 기계적으로 메서드를 늘릴 필요 없이,
     * 필요한 시점에 필요한 메서드만 추가하면 된다.
     *
     * 3. X-Account-Id 를 헤더로 보내는 이유
     * --------------------------------------------------------------------
     * account-service 의 /internal/accounts/{id} 는 단순 ID 기반 조회이므로
     * JWT 가 필요 없다. 대신 두 가지 헤더가 필요하다.
     *
     *   X-Internal-Service: frontend-service
     *     -> InternalRequestFilter 가 이 헤더를 보고 내부 서비스임을 확인.
     *        이 헤더가 없으면 403 을 반환하도록 account-service 에 설정되어 있다.
     *
     *   X-Account-Id: {id}
     *     -> 조회할 계정의 PK. URL 의 {id} 와 동일하지만,
     *        account-service 가 필요에 따라 "요청자와 조회 대상이 동일한지"를
     *        확인하는 데 활용할 수 있다.
     *
     * InternalHeaderHelper.build(accountId) 가 이 두 헤더를 한 번에 담은
     * HttpEntity<Void> 를 반환하기 때문에, 모든 클라이언트에서
     * 동일한 헬퍼를 재사용하면 헤더 누락 실수를 방지할 수 있다.
     * ====================================================================
     */
}