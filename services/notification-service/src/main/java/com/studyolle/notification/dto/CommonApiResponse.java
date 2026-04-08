package com.studyolle.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommonApiResponse<T> {

    private final boolean success;
    private final String message;
    private final T data;

    // 데이터만 반환
    public static <T> CommonApiResponse<T> ok(T data) {
        return CommonApiResponse.<T>builder()
                .success(true)
                .data(data)
                .build();
    }

    // 메시지 + 데이터 반환
    public static <T> CommonApiResponse<T> ok(String message, T data) {
        return CommonApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    // 메시지만 반환 (데이터 없는 처리 완료)
    public static CommonApiResponse<Void> ok(String message) {
        return CommonApiResponse.<Void>builder()
                .success(true)
                .message(message)
                .build();
    }
}