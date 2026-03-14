package com.studyolle.frontend.study.client;

import com.studyolle.frontend.study.dto.AccountSummaryDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * frontend-service -> account-service 내부 API 호출 클라이언트.
 *
 * 네비게이션 바 + 대시보드 프로필 완성도 계산을 위해
 * 현재 사용자의 계정 정보를 가져온다.
 */
@Component
public class AccountInternalClient {

    private final RestTemplate restTemplate;

    @Value("${app.account-service-base-url:lb://ACCOUNT-SERVICE}")
    private String accountServiceBaseUrl;

    public AccountInternalClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 계정 요약 정보 조회.
     * account-service: GET /internal/accounts/{id}
     *
     * @param accountId X-Account-Id 헤더에서 읽은 사용자 ID
     * @return AccountSummaryDto, 없으면 null
     */
    public AccountSummaryDto getAccountSummary(Long accountId) {
        if (accountId == null) return null;

        String url = accountServiceBaseUrl + "/internal/accounts/" + accountId;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Service", "frontend-service");
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<AccountSummaryDto> response =
                    restTemplate.exchange(url, HttpMethod.GET, entity, AccountSummaryDto.class);
            return response.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }
}