package com.studyolle.account.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

// null인 필드는 JSON에서 제외 (data가 없는 단순 성공 응답 시 깔끔하게 출력)
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommonApiResponse<T> {

    // 요청 성공 여부
    private final boolean success;

    // 성공 시 안내 메시지, 실패 시 에러 메시지
    private final String message;

    // 실제 응답 데이터 (없으면 null → JSON에서 제외됨)
    private final T data;

    // === 정적 팩터리 메서드들 ===

    // 데이터만 있는 성공 응답: ApiResponse.ok(tokenResponse)
    public static <T> CommonApiResponse<T> ok(T data) {
        return CommonApiResponse.<T>builder()
                .success(true)
                .data(data)
                .build();
    }

    // 메시지 + 데이터 성공 응답: ApiResponse.ok("프로필 수정 완료", accountResponse)
    public static <T> CommonApiResponse<T> ok(String message, T data) {
        return CommonApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    // 데이터 없는 단순 성공 응답: ApiResponse.ok("이메일 재발송 완료")
    public static CommonApiResponse<Void> ok(String message) {
        return CommonApiResponse.<Void>builder()
                .success(true)
                .message(message)
                .build();
    }

}