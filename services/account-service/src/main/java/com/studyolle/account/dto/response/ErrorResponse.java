package com.studyolle.account.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ErrorResponse {

    // HTTP 상태 코드 (예: 400, 401, 404)
    private final int status;

    // 에러 코드 (예: "EMAIL_DUPLICATE", "INVALID_TOKEN")
    // 프론트엔드에서 이 코드를 보고 적절한 메시지를 표시할 수 있음
    private final String errorCode;

    // 사람이 읽을 수 있는 에러 설명
    private final String message;

    public static ErrorResponse of(int status, String errorCode, String message) {
        return ErrorResponse.builder()
                .status(status)
                .errorCode(errorCode)
                .message(message)
                .build();
    }
}