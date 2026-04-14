package com.studyolle.adminfrontend.member.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * admin-service 가 반환하는 CommonApiResponse 구조를 받기 위한 미러 DTO.
 *
 * Lombok 의 @Getter 는 primitive boolean 필드에 대해 "isXxx()" 형태의 getter 를 생성한다.
 * (Boolean 래퍼면 getXxx() 를 생성한다.)
 * 따라서 호출 측에서는 body.isSuccess() 로 사용한다.
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