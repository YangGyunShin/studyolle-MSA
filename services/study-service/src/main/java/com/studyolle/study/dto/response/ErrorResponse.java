package com.studyolle.study.dto.response;

import lombok.Builder;
import lombok.Getter;

/** account-service 의 ErrorResponse 와 동일한 패턴 */
@Getter
@Builder
public class ErrorResponse {

    private final int status;
    private final String errorCode;
    private final String message;

    public static ErrorResponse of(int status, String errorCode, String message) {
        return ErrorResponse.builder()
                .status(status)
                .errorCode(errorCode)
                .message(message)
                .build();
    }
}