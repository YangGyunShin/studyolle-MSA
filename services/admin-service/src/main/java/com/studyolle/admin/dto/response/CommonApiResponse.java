package com.studyolle.admin.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

/**
 * admin-service 의 모든 /api/admin/** 응답을 감싸는 공통 래퍼.
 *
 * [왜 래퍼가 필요한가 — 일관된 계약]
 * REST API 응답을 "데이터만" 반환하면 "성공인지 실패인지", "실패면 왜인지",
 * "성공이라도 사용자에게 보여줄 메시지가 있는지" 같은 메타 정보를 담을 곳이 없다.
 * HTTP 상태 코드만으로는 "200 OK 인데 비즈니스 로직상 실패" 같은 경우를 표현하기 어렵다.
 *
 * 이 래퍼는 모든 응답을 세 부분으로 구조화한다:
 *   success : 비즈니스 로직 성공 여부 (HTTP 상태와 독립적)
 *   message : 사용자에게 표시할 메시지
 *   data    : 실제 페이로드
 *
 * [@JsonInclude(NON_NULL) 의 역할]
 * 이 어노테이션이 있으면 null 인 필드는 JSON 응답에서 아예 제외된다.
 * 예를 들어 data 없이 message 만 반환할 때 "data": null 이 빠져 응답이 깔끔해진다.
 * 네트워크 대역폭 절약도 되지만 더 중요한 점은 프론트에서 필드 존재 여부로 분기할 수 있다는 것이다.
 *
 * [왜 각 서비스가 자기 CommonApiResponse 를 가지는가]
 * 공유 라이브러리로 빼지 않는 이유는 독립 배포 원칙 때문이다. 각 서비스가 자기 소유의
 * 복사본을 가짐으로써 한 서비스의 응답 포맷 변경이 다른 서비스의 재배포를 강제하지 않는다.
 * 구조가 동일한 "의도적 중복" 이지 "잘못된 중복" 이 아니다.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommonApiResponse<T> {

    private final boolean success;
    private final String message;
    private final T data;

    /**
     * 조회 성공 — 데이터만 반환.
     * 정적 팩토리 메서드로 감싸면 호출 측 코드가 깔끔해진다:
     *   return CommonApiResponse.ok(account);
     */
    public static <T> CommonApiResponse<T> ok(T data) {
        return CommonApiResponse.<T>builder()
                .success(true)
                .data(data)
                .build();
    }

    /**
     * 성공 + 메시지 + 데이터 반환.
     * 예: "수정이 완료되었습니다" 와 함께 수정된 엔티티 반환.
     */
    public static <T> CommonApiResponse<T> ok(String message, T data) {
        return CommonApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    /**
     * 성공 알림만 반환 (데이터 없음).
     * 예: "삭제되었습니다" 같이 반환할 데이터가 없는 처리 완료 응답.
     */
    public static CommonApiResponse<Void> ok(String message) {
        return CommonApiResponse.<Void>builder()
                .success(true)
                .message(message)
                .build();
    }

    /**
     * 실패 응답 — 메시지만 반환 (데이터 없음).
     *
     * 비즈니스 로직 실패를 표현하기 위한 정적 팩토리.
     * 예: 자기 자신 권한 변경 시도, 유효하지 않은 입력 등.
     *
     * HTTP 상태 코드는 호출 측의 ResponseEntity 빌더에서 별도로 정한다.
     * (이 래퍼는 비즈니스 성공/실패만 표현하고, 전송 계층의 상태는 관여하지 않는다.)
     *
     * 예시:
     *   return ResponseEntity.badRequest()
     *           .body(CommonApiResponse.fail("자기 자신의 권한은 변경할 수 없습니다."));
     */
    public static <T> CommonApiResponse<T> fail(String message) {
        return CommonApiResponse.<T>builder()
                .success(false)
                .message(message)
                .build();
    }
}