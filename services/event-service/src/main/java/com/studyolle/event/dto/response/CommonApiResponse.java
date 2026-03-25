package com.studyolle.event.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CommonApiResponse {
    private boolean success;
    private String message;

    public static CommonApiResponse ok(String message) {
        return new CommonApiResponse(true, message);
    }

    public static CommonApiResponse fail(String message) {
        return new CommonApiResponse(false, message);
    }
}