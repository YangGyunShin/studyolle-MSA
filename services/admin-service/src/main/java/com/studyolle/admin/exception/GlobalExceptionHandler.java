package com.studyolle.admin.exception;

import com.studyolle.admin.dto.response.CommonApiResponse;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * admin-service 의 전역 예외 처리기.
 *
 * [@RestControllerAdvice 의 동작 원리]
 * 이 어노테이션이 붙은 클래스는 모든 @RestController 에 대해 "횡단 관심사" 로 작동한다.
 * 컨트롤러 메서드가 예외를 던지면 Spring 이 자동으로 여기서 매칭되는 @ExceptionHandler 를 찾는다.
 * 매칭이 되면 그 핸들러를 호출해서 반환값을 HTTP 응답으로 변환한다.
 * 매칭이 되지 않으면 Spring 의 기본 동작으로 500 Internal Server Error 가 나간다.
 *
 * [핸들러 매칭 규칙 — 가장 구체적인 것부터]
 * 아래 handleFeignNotFound 와 handleFeign 을 보면 타입 계층이 있다:
 *   FeignException                  ← 부모
 *   FeignException.NotFound         ← 자식 (404 전용)
 * NotFound 예외가 던져지면 Spring 은 더 구체적인 handleFeignNotFound 를 먼저 호출한다.
 * 일반 FeignException 만 있다면 그때 handleFeign 이 호출된다.
 *
 * [왜 이것이 꼭 필요한가]
 * 1. 응답 형식 통일 — 에러도 CommonApiResponse 구조여야 프론트가 한 가지 방식으로 처리 가능
 * 2. 의존 서비스 장애를 사용자 친화적 메시지로 변환
 * 3. 내부 구현 세부 정보(스택 트레이스, SQL 오류, 포트 번호 등) 가 외부로 새는 것 방지
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 잘못된 요청 파라미터, 검증 실패 등 클라이언트 측 오류.
     * 400 Bad Request 로 반환한다.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<CommonApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("잘못된 요청: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(CommonApiResponse.<Void>builder()
                        .success(false)
                        .message(e.getMessage())
                        .build());
    }

    /**
     * Feign 호출 대상 서비스가 404 를 반환한 경우.
     *
     * 예: admin-service 가 account-service 에 ID 999 로 회원을 조회했는데 해당 회원이 없는 경우.
     * 이를 500 으로 반환하면 "서버 장애" 로 보이지만, 실제로는 "요청한 리소스가 없음" 이다.
     * 의미가 다르므로 HTTP 상태 코드도 404 로 돌려주는 것이 맞다.
     */
    @ExceptionHandler(FeignException.NotFound.class)
    public ResponseEntity<CommonApiResponse<Void>> handleFeignNotFound(FeignException.NotFound e) {
        log.warn("의존 서비스에서 리소스를 찾지 못함: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(CommonApiResponse.<Void>builder()
                        .success(false)
                        .message("요청한 리소스를 찾을 수 없습니다.")
                        .build());
    }

    /**
     * Feign 호출 실패 전체 — 위의 NotFound 에 잡히지 않은 모든 경우.
     *
     * 예: 대상 서비스가 다운되어 Connection Refused, 5xx 응답, 타임아웃 등.
     * 이것은 "내 서버의 문제" 가 아니라 "내가 의존하는 서버의 문제" 이므로
     * 502 Bad Gateway 가 의미론적으로 맞다. HTTP 스펙상 502 의 정의가 바로 이것이다.
     */
    @ExceptionHandler(FeignException.class)
    public ResponseEntity<CommonApiResponse<Void>> handleFeign(FeignException e) {
        log.error("Feign 호출 실패: status={}, message={}", e.status(), e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(CommonApiResponse.<Void>builder()
                        .success(false)
                        .message("의존 서비스 호출에 실패했습니다. 잠시 후 다시 시도해주세요.")
                        .build());
    }

    /**
     * 예상하지 못한 모든 예외의 최종 방어선.
     *
     * NullPointerException, DB 오류 등은 사용자에게 내부 메시지를 노출하면 안 된다.
     * 서버 로그에는 전체 스택 트레이스를 남기고 (log.error 의 두 번째 인자에 예외 전달),
     * 클라이언트에는 일반적인 메시지만 반환한다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonApiResponse<Void>> handleGeneric(Exception e) {
        log.error("admin-service 예상치 못한 오류", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(CommonApiResponse.<Void>builder()
                        .success(false)
                        .message("서버 내부 오류가 발생했습니다.")
                        .build());
    }
}