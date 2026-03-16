package com.studyolle.frontend.common;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;

/**
 * 내부 서비스 간 호출에 공통으로 필요한 헤더를 생성하는 유틸리티 클래스.
 *
 * 모든 InternalClient 는 이 클래스를 통해 HttpEntity 를 만든다.
 * 헤더를 직접 생성하는 코드를 각 클라이언트에 중복해서 작성하면,
 * 나중에 헤더 이름이 바뀌거나 새 헤더가 추가될 때 모든 파일을 수정해야 한다.
 * 이 클래스 하나만 수정하면 전체에 반영되도록 중앙화한 것이다.
 */
public class InternalHeaderHelper {

    private InternalHeaderHelper() {
        // 유틸리티 클래스이므로 인스턴스 생성 금지
    }

    /**
     * X-Internal-Service 헤더를 포함한 HttpEntity 를 생성한다.
     *
     * @param accountId null 이면 X-Account-Id 헤더를 추가하지 않는다.
     */
    public static HttpEntity<Void> build(Long accountId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Service", "frontend-service");
        if (accountId != null) {
            headers.set("X-Account-Id", String.valueOf(accountId));
        }
        return new HttpEntity<>(headers);
    }
}