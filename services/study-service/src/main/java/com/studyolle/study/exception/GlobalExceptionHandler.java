package com.studyolle.study.exception;

import com.studyolle.study.dto.response.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 전역 예외 처리기.
 *
 * =============================================
 * @RestControllerAdvice 란?
 * =============================================
 *
 * @ControllerAdvice 는 애플리케이션 전체에서 발생하는 예외를 한 곳에서 처리할 수 있게 해주는 어노테이션이다.
 * @RestControllerAdvice 는 @ControllerAdvice + @ResponseBody 의 조합으로,
 * 예외 처리 결과를 JSON 으로 직렬화하여 응답 바디에 담는다.
 *
 * 이 클래스가 없으면 예외가 발생했을 때 Spring 의 기본 에러 페이지(HTML)가 반환된다.
 * REST API 에서는 JSON 형식의 에러 응답이 필요하므로 반드시 구현해야 한다.
 *
 * =============================================
 * 예외 분류 기준
 * =============================================
 *
 * MethodArgumentNotValidException → 400 Bad Request
 *   @Valid 검증 실패 시 Spring 이 자동으로 던진다.
 *   예: CreateStudyRequest 의 @NotBlank path 필드가 비어있을 때.
 *
 * IllegalArgumentException → 400 Bad Request
 *   잘못된 입력값으로 인한 비즈니스 오류.
 *   예: 존재하지 않는 스터디 경로, 이미 사용 중인 경로, 존재하지 않는 지역.
 *
 * IllegalStateException → 403 Forbidden
 *   현재 상태에서 허용되지 않는 작업.
 *   예: 관리자가 아닌 사용자의 설정 변경 시도, 이미 공개된 스터디 재공개 시도.
 *
 * Exception → 500 Internal Server Error
 *   위 세 가지에 해당하지 않는 예상치 못한 서버 오류.
 *   스택 트레이스를 서버 로그에만 남기고 클라이언트에는 일반적인 메시지를 반환한다.
 *   (스택 트레이스를 클라이언트에 노출하면 내부 구조가 외부에 드러나는 보안 취약점이 된다.)
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * @Valid 검증 실패 처리 — 400 Bad Request
     *
     * MethodArgumentNotValidException 은 @Valid 가 붙은 @RequestBody 의 유효성 검사가
     * 실패했을 때 Spring 이 자동으로 던지는 예외다.
     *
     * getBindingResult().getFieldErrors() 로 어느 필드에서 어떤 검증이 실패했는지 알 수 있다.
     * 여러 필드가 동시에 실패할 수 있으므로 FieldError 들을 ", " 로 이어붙여 하나의 메시지로 만든다.
     *
     * 예시 응답: { "status": 400, "code": "VALIDATION_ERROR", "message": "path 는 필수입니다, title 은 필수입니다" }
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, "VALIDATION_ERROR", message));
    }

    /**
     * 잘못된 입력값 오류 처리 — 400 Bad Request
     *
     * 존재하지 않는 스터디, 이미 사용 중인 경로, 잘못된 가입 방식 등
     * 클라이언트가 보낸 데이터에 문제가 있을 때 서비스/엔티티에서 던지는 예외다.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, "BAD_REQUEST", e.getMessage()));
    }

    /**
     * 상태 기반 접근 거부 처리 — 403 Forbidden
     *
     * 관리자가 아닌 사용자의 접근, 이미 종료된 스터디 재종료 등
     * 현재 상태에서 허용되지 않는 작업을 시도할 때 서비스/엔티티에서 던지는 예외다.
     * HTTP 상태 코드 403 은 "요청은 유효하지만 권한이 없어 거부됨"을 의미한다.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(403, "FORBIDDEN", e.getMessage()));
    }

    /**
     * 예상치 못한 서버 오류 처리 — 500 Internal Server Error
     *
     * 위 세 가지 예외에 해당하지 않는 모든 예외를 잡는 최후의 안전망이다.
     * log.error 로 스택 트레이스를 서버 로그에 남겨 디버깅할 수 있게 하고,
     * 클라이언트에는 내부 구조를 노출하지 않는 일반적인 메시지만 반환한다.
     *
     * @Slf4j 는 Lombok 이 제공하는 어노테이션으로,
     * private static final Logger log = LoggerFactory.getLogger(this.getClass()); 를 자동 생성한다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(500, "INTERNAL_ERROR", "서버 내부 오류가 발생했습니다."));
    }
}