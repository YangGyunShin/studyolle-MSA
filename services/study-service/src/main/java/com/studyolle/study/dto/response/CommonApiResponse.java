package com.studyolle.study.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

/** account-service 의 CommonApiResponse 와 동일한 패턴 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommonApiResponse<T> {

    private final boolean success;
    private final String message;
    private final T data;

    public static <T> CommonApiResponse<T> ok(T data) {
        return CommonApiResponse.<T>builder()
                .success(true)
                .data(data)
                .build();
    }

    public static <T> CommonApiResponse<T> ok(String message, T data) {
        return CommonApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    public static CommonApiResponse<Void> ok(String message) {
        return CommonApiResponse.<Void>builder()
                .success(true)
                .message(message)
                .build();
    }
}