package com.studyolle.frontend.admin.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * admin-service 가 반환하는 CommonApiResponse 구조를 받기 위한 frontend 측 미러 DTO.
 *
 * [왜 isSuccess 인가 — primitive boolean + Lombok 의 동작]
 * Lombok 의 @Getter 는 boolean 필드에 대해 "isXxx()" 형태의 getter 를 생성한다.
 * (참고: Boolean 래퍼 타입이면 getXxx() 를 생성한다)
 * 따라서 호출 측에서는 body.isSuccess() 로 사용한다.
 *
 * [data 가 T 제네릭인 이유]
 * 회원 목록, 스터디 목록, 단일 리소스 등 여러 형태의 응답에 재사용할 수 있게 제네릭으로 두었다.
 * 실제 호출 시점에는 AdminCommonApiResponse<AdminPageResponse<AdminMemberDto>> 처럼
 * 구체 타입을 지정한다.
 */
@Getter
public class AdminCommonApiResponse<T> {

    private final boolean success;
    private final String message;
    private final T data;

    @JsonCreator
    public AdminCommonApiResponse(
            @JsonProperty("success") boolean success,
            @JsonProperty("message") String message,
            @JsonProperty("data") T data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }
}