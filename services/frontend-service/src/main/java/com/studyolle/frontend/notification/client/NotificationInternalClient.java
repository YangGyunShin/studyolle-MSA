package com.studyolle.frontend.notification.client;

import com.studyolle.frontend.common.InternalHeaderHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * notification-service 와 통신하는 내부 클라이언트.
 *
 * =============================================
 * 이 클라이언트의 역할이 매우 작은 이유
 * =============================================
 *
 * 알림 페이지 자체의 데이터(목록 조회, 읽음 처리)는 브라우저가 직접
 * fetch(/api/notifications) 로 api-gateway 를 거쳐 호출한다.
 * frontend-service 는 알림 목록 조회에 관여하지 않는다.
 *
 * 이 클라이언트가 담당하는 것은 단 하나:
 *   "nav 바 🔔 뱃지의 안 읽은 알림 수를 서버 사이드에서 미리 가져오기"
 *
 * 모든 페이지에서 nav 뱃지가 표시되어야 하므로
 * GlobalModelAttributes(@ControllerAdvice) 가 모든 페이지 렌더링 전에
 * 이 메서드를 호출해 unreadNotificationCount 를 모델에 주입한다.
 *
 * =============================================
 * Redis 즉시 반환의 효과
 * =============================================
 *
 * notification-service 의 GET /internal/notifications/count/{accountId} 는
 * Redis 에서 카운터 값만 즉시 반환한다 (PostgreSQL COUNT 쿼리 X).
 * 모든 페이지 로드마다 호출되어도 부하가 거의 없다 (~0.1ms).
 */
@Component
@RequiredArgsConstructor
public class NotificationInternalClient {

    private final RestTemplate restTemplate;

    @Value("${app.notification-service-base-url:lb://NOTIFICATION-SERVICE}")
    private String notificationServiceBaseUrl;

    /**
     * 안 읽은 알림 수 조회.
     *
     * notification-service: GET /internal/notifications/count/{accountId}
     * 응답 형식: { "count": 5 }
     *
     * @param accountId null 이면 비로그인이므로 0 반환
     * @return 안 읽은 알림 수. 호출 실패 시 0 (서비스 장애가 페이지 렌더링을 막지 않도록)
     */
    public long getUnreadCount(Long accountId) {
        if (accountId == null) {
            return 0L;
        }

        String url = notificationServiceBaseUrl + "/internal/notifications/count/" + accountId;

        try {
            ResponseEntity<Map<String, Long>> response = restTemplate.exchange(
                    url,                                                      // ① 호출할 URL (lb:// 접두사 → Eureka 로드밸런싱)
                    HttpMethod.GET,                                           // ② HTTP 메서드
                    InternalHeaderHelper.build(accountId),                    // ③ 헤더 (X-Internal-Service + X-Account-Id), 바디 없음
                    new ParameterizedTypeReference<Map<String, Long>>() {}    // ④ 응답 타입 — 익명 클래스로 제네릭 정보 보존
            );

            // response.getBody() 가 null 인 경우는 거의 없지만 (HTTP 200 인데 바디 없음),
            // 방어적으로 처리하지 않으면 NullPointerException 위험이 있어 명시적으로 체크.
            Map<String, Long> body = response.getBody();
            if (body == null) {
                return 0L;
            }

            // notification-service 의 응답 형식: { "count": 5 }
            // → body.get("count") 로 Long 값을 꺼낸다.
            // 키가 없거나 값이 null 이면 (이론상 발생 안 함) 0 으로 안전하게 fallback.
            Long count = body.get("count");
            return count != null ? count : 0L;

        } catch (HttpClientErrorException e) {
            // notification-service 가 다운되거나 Redis 가 다운된 경우.
            // 0 을 반환해 nav 뱃지가 사라질 뿐, 페이지 렌더링은 정상 진행된다.
            return 0L;
        } catch (Exception e) {
            return 0L;
        }
    }
}
/*
 * ====================================================================
 * RestTemplate.exchange() 호출 — 한 줄씩 풀어서 설명
 * ====================================================================
 *
 * RestTemplate 은 Spring 이 제공하는 동기식 HTTP 클라이언트다.
 * exchange() 는 그중 가장 유연한 메서드로, HTTP 메서드/헤더/바디/응답 타입을
 * 모두 직접 지정할 수 있다. (getForObject, postForEntity 등은 exchange 의
 * 단순화된 버전이다.)
 *
 * 메서드 시그니처:
 *   <T> ResponseEntity<T> exchange(
 *       String url,                          // ① 요청 URL
 *       HttpMethod method,                   // ② HTTP 메서드 (GET/POST/...)
 *       HttpEntity<?> requestEntity,         // ③ 요청 헤더 + 바디
 *       ParameterizedTypeReference<T> type   // ④ 응답 타입 (제네릭 보존용)
 *   );
 *
 * ① url
 *    "lb://NOTIFICATION-SERVICE/internal/notifications/count/123" 같은 문자열.
 *    lb:// 접두사가 있으면 LoadBalancer 가 Eureka 에서 실제 인스턴스 주소
 *    (예: http://192.168.x.x:8085) 로 치환한 뒤 호출한다.
 *
 * ② HttpMethod.GET
 *    HTTP 메서드 enum. GET / POST / PUT / DELETE / PATCH 등.
 *
 * ③ InternalHeaderHelper.build(accountId)
 *    HttpEntity<Void> 를 반환하는 헬퍼.
 *    내부적으로 HttpHeaders 에 다음 두 헤더를 담는다:
 *      X-Internal-Service: frontend-service   ← 내부 서비스 식별용
 *      X-Account-Id: {accountId}              ← 사용자 식별용
 *    GET 요청이라 바디는 없으므로 제네릭 타입이 Void 다.
 *
 * ④ new ParameterizedTypeReference<Map<String, Long>>() {}
 *    *** 이 부분이 가장 중요하다. 아래에서 별도로 설명한다. ***
 *
 * 반환값: ResponseEntity<Map<String, Long>>
 *    ResponseEntity 는 응답을 감싸는 래퍼 객체로, 다음 정보를 모두 담는다:
 *      - 상태 코드 (200, 404, 500 등)        → response.getStatusCode()
 *      - 응답 헤더                            → response.getHeaders()
 *      - 응답 바디 (실제 데이터)              → response.getBody()
 *    여기서는 바디만 필요하므로 response.getBody() 로 Map 을 꺼낸다.
 *
 * --------------------------------------------------------------------
 * 왜 ParameterizedTypeReference 가 필요한가? — Java 의 Type Erasure
 * --------------------------------------------------------------------
 *
 * 단순한 응답 타입이라면 .class 를 쓸 수 있다:
 *   restTemplate.exchange(url, GET, entity, String.class);
 *   restTemplate.exchange(url, GET, entity, AccountSummaryDto.class);
 *
 * 그런데 응답 타입이 제네릭이면 문제가 생긴다:
 *   restTemplate.exchange(url, GET, entity, Map<String, Long>.class);  // ❌ 컴파일 에러!
 *
 * 자바 컴파일러는 "Map<String, Long>.class" 같은 문법을 허용하지 않는다.
 * 그 이유는 자바의 **Type Erasure(타입 소거)** 때문이다.
 *
 * 자바 제네릭은 컴파일 시점까지만 타입 정보를 유지하고, 컴파일이 끝나면
 * 바이트코드에서 모든 제네릭 정보가 사라진다. 즉 런타임에는:
 *   Map<String, Long>     →     Map  (그냥 raw Map)
 *   List<Integer>          →     List
 *   Set<Account>           →     Set
 * 이렇게 된다. 그래서 Map<String, Long>.class 같은 것이 존재할 수 없다.
 * 그냥 Map.class 만 존재한다.
 *
 * 그런데 RestTemplate 입장에서는 Jackson 으로 JSON 을 역직렬화하려면
 * "이 JSON 을 Map<String, Long> 으로 만들어줘" 라고 정확히 알아야 한다.
 * Map.class 만 알려주면 Jackson 은 값의 타입을 알 수 없어 Object 로 처리한다.
 *
 * --------------------------------------------------------------------
 * ParameterizedTypeReference 의 트릭: 익명 클래스 + super type token
 * --------------------------------------------------------------------
 *
 *   new ParameterizedTypeReference<Map<String, Long>>() {}
 *                                                       ^^
 *                                                       이 중괄호가 핵심
 *
 * 끝의 {} 는 ParameterizedTypeReference 를 상속하는 **익명 클래스** 를
 * 그 자리에서 만든다는 뜻이다. 다음과 같은 클래스를 만든 것과 같다:
 *
 *   class Anon extends ParameterizedTypeReference<Map<String, Long>> { }
 *
 * 자바의 Type Erasure 에는 한 가지 예외가 있다:
 * **클래스의 슈퍼클래스에 명시된 제네릭 타입은 런타임에도 유지된다.**
 * 즉 Anon 클래스의 부모가 ParameterizedTypeReference<Map<String, Long>> 라는
 * 정보는 .class 파일에 그대로 남는다.
 *
 * ParameterizedTypeReference 의 생성자는 리플렉션으로 자기 자신의
 * "슈퍼클래스 제네릭 정보"를 읽어서 Map<String, Long> 이라는 정확한 타입
 * 정보를 추출한다. 이 기법을 **Super Type Token 패턴**이라고 부른다.
 *
 * 결과적으로 RestTemplate 은 "응답을 Map<String, Long> 으로 만들어줘" 라는
 * 정확한 지시를 받게 되고, Jackson 이 JSON 을 그 타입으로 역직렬화한다.
 *
 * --------------------------------------------------------------------
 * 정리: 언제 .class 를 쓰고 언제 ParameterizedTypeReference 를 쓰나?
 * --------------------------------------------------------------------
 *
 *   응답 타입이 제네릭 X         →  ClassName.class
 *     예) AccountSummaryDto.class, String.class
 *
 *   응답 타입이 제네릭 O         →  new ParameterizedTypeReference<...>() {}
 *     예) Map<String, Long>, List<AccountDto>, Page<StudyDto>
 *
 * 이 클라이언트의 응답은 { "count": 5 } 같은 JSON 인데,
 * 이를 Map<String, Long> 으로 받기 때문에 ParameterizedTypeReference 가 필요하다.
 * ====================================================================
 */