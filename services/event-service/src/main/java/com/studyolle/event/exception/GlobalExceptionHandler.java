package com.studyolle.event.exception;

import com.studyolle.event.dto.response.CommonApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<CommonApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(CommonApiResponse.<Void>builder()
                        .success(false)
                        .message(e.getMessage())
                        .build());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<CommonApiResponse<Void>> handleIllegalState(IllegalStateException e) {
        return ResponseEntity.status(403)
                .body(CommonApiResponse.<Void>builder()
                        .success(false)
                        .message(e.getMessage())
                        .build());
    }
}