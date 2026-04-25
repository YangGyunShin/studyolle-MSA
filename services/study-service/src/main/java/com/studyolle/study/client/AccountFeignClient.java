package com.studyolle.study.client;

import com.studyolle.study.client.dto.AccountBatchRequest;
import com.studyolle.study.client.dto.AccountSummaryDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

/**
 * account-service 내부 API 호출 Feign Client.
 *
 * =============================================
 * 이 클라이언트가 하는 일
 * =============================================
 *
 * study-service 가 스터디 페이지를 렌더링할 때 멤버들의 닉네임과 프로필 이미지를
 * 한 번의 네트워크 호출로 가져오기 위한 클라이언트다.
 *
 * =============================================
 * @FeignClient(name = "account-service")
 * =============================================
 *
 * "account-service" 는 Eureka 에 등록된 서비스 이름이다.
 * Feign 이 HTTP 요청을 보낼 때 Eureka 에서 실제 host:port 를 조회해 로드 밸런싱을 적용한다.
 * 서비스 인스턴스가 추가되거나 주소가 바뀌어도 이 코드는 수정할 필요가 없다.
 *
 * =============================================
 * X-Internal-Service 헤더
 * =============================================
 *
 * account-service 의 InternalRequestFilter 가 /internal/** 경로에 대해
 * X-Internal-Service 헤더를 검증한다. 이 헤더가 없으면 403 을 반환한다.
 * 따라서 메서드 파라미터에 @RequestHeader 를 선언하고, 호출 시 "study-service" 를 넘겨야 한다.
 *
 * =============================================
 * 왜 한 개 메서드만 있는가 — 지금 당장 필요한 것만
 * =============================================
 *
 * 필요에 의해 점진적으로 추가하면 된다. 지금은 batch 조회 한 가지 용도만 있으므로 메서드도 하나다.
 * 나중에 "특정 회원 프로필만 조회" 같은 단건 조회 니즈가 생기면 그때 추가한다.
 * Feign 인터페이스는 "쓰는 만큼만 선언한다" 가 일반적인 관례다.
 */
@FeignClient(name = "account-service")
public interface AccountFeignClient {

    /**
     * 여러 계정 id 를 한 번에 조회해서 id → AccountSummaryDto 의 map 으로 받는다.
     *
     * account-service: POST /internal/accounts/batch
     *
     * 요청 본문: { "ids": [1, 5, 12] }
     * 응답 본문: { "1": {...}, "5": {...}, "12": {...} }
     *
     * 요청한 id 중 존재하지 않는 것이 섞여 있으면 map 에서 키가 빠져 있을 뿐,
     * 예외가 나지 않는다. 이것이 batch API 의 관용적 동작이다.
     *
     * @param request     ids 가 담긴 요청 본문
     * @param serviceName "study-service" — InternalRequestFilter 통과용
     */
    @PostMapping("/internal/accounts/batch")
    Map<Long, AccountSummaryDto> getAccountsBatch(
            @RequestBody AccountBatchRequest request,
            @RequestHeader("X-Internal-Service") String serviceName);
}