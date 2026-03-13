package com.studyolle.study.dto.response;

import lombok.Builder;
import lombok.Getter;

// API 오류 발생 시 클라이언트에 반환하는 에러 응답 DTO.
// GlobalExceptionHandler 에서 예외를 잡아 이 객체를 생성하고 반환한다.
@Getter
@Builder
public class ErrorResponse {

    // HTTP 상태 코드. 예: 400, 403, 404, 500
    // HttpStatus 열거형 대신 int 를 쓰는 이유: JSON 직렬화가 단순해지고
    // 클라이언트가 숫자로 바로 비교할 수 있다
    private final int status;

    // 에러 종류를 나타내는 코드 문자열. 예: "VALIDATION_ERROR", "FORBIDDEN"
    // 클라이언트(프론트엔드)가 이 값을 보고 사용자에게 표시할 메시지를 결정할 수 있다.
    // HTTP 상태 코드만으로는 같은 400 이라도 원인이 다를 수 있으므로 세분화한다
    private final String errorCode;

    // 사람이 읽을 수 있는 에러 설명. 예: "스터디 경로가 이미 사용 중입니다."
    private final String message;

    // 정적 팩터리 메서드. new ErrorResponse.builder()... 대신 한 줄로 간결하게 생성한다
    public static ErrorResponse of(int status, String errorCode, String message) {
        return ErrorResponse.builder()
                .status(status)
                .errorCode(errorCode)
                .message(message)
                .build();
    }
}

/*
 * [CommonApiResponse 와 ErrorResponse 를 분리하는 이유]
 *
 * 성공 응답과 실패 응답의 구조가 다르다.
 * 성공: {"success": true, "data": {...}}
 * 실패: {"status": 400, "errorCode": "VALIDATION_ERROR", "message": "..."}
 *
 * 하나의 클래스로 합치면 성공 응답에도 status, errorCode 필드가 생기거나,
 * 실패 응답에도 data 필드가 생겨 구조가 모호해진다.
 * 분리하면 각 클래스의 역할이 명확해지고 JSON 응답 구조도 깔끔해진다.
 *
 *
 * [errorCode 를 별도로 두는 이유]
 *
 * HTTP 상태 코드(400)는 "잘못된 요청" 이라는 큰 범주만 알려준다.
 * 하지만 400 이 발생하는 원인은 매우 다양하다:
 *   - 입력값 형식 오류(VALIDATION_ERROR)
 *   - 이미 사용 중인 경로(PATH_DUPLICATE)
 *   - 이미 가입한 스터디(ALREADY_MEMBER)
 *
 * 프론트엔드는 errorCode 를 보고 각 상황에 맞는 사용자 메시지를 표시할 수 있다.
 * message 필드의 한국어 문자열은 개발자가 디버깅할 때 참조하는 용도다.
 *
 *
 * [@Builder 와 정적 팩터리 메서드를 함께 쓰는 이유]
 *
 * @Builder 만 있으면 ErrorResponse.builder().status(400).errorCode("...").message("...").build()
 * 처럼 4줄이 필요하다.
 * 정적 팩터리 메서드 of() 를 추가하면 ErrorResponse.of(400, "VALIDATION_ERROR", "...")
 * 한 줄로 줄어든다.
 * GlobalExceptionHandler 에서 반복적으로 호출되므로 간결함이 중요하다.
 */
