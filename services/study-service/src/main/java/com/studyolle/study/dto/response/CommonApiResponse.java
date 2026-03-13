package com.studyolle.study.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

// 모든 API 응답을 감싸는 공통 래퍼 클래스.
// 제네릭 <T> 를 사용해 실제 데이터 타입에 관계없이 동일한 응답 구조를 유지한다.
// @JsonInclude(NON_NULL): null 인 필드는 JSON 에 포함하지 않는다.
//   데이터 없는 응답에서 "data": null 이 노출되지 않도록 한다.
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommonApiResponse<T> {

    // 요청 처리 성공 여부. 항상 포함된다
    private final boolean success;

    // 처리 결과 메시지. 데이터가 없는 단순 성공/실패 응답에 주로 사용
    // null 이면 JSON 에서 제외됨 (@JsonInclude 효과)
    private final String message;

    // 실제 응답 데이터. 없는 경우 null 이고 JSON 에서 제외됨
    private final T data;

    // 데이터만 있는 성공 응답.
    // 예: ResponseEntity.ok(CommonApiResponse.ok(studyResponse))
    // → {"success": true, "data": {...}}
    public static <T> CommonApiResponse<T> ok(T data) {
        return CommonApiResponse.<T>builder()
                .success(true)
                .data(data)
                .build();
    }

    // 메시지와 데이터를 함께 반환하는 성공 응답.
    // 예: 생성 완료 메시지와 생성된 객체를 동시에 반환할 때
    // → {"success": true, "message": "생성됨", "data": {...}}
    public static <T> CommonApiResponse<T> ok(String message, T data) {
        return CommonApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    // 메시지만 있는 성공 응답. 반환 데이터가 없을 때 사용.
    // 반환 타입이 CommonApiResponse<Void> 이므로
    // 컨트롤러 반환 타입도 ResponseEntity<CommonApiResponse<Void>> 로 선언해야 한다.
    // 예: 가입, 탈퇴, 설정 변경 등 처리 완료 메시지만 돌려주는 경우
    // → {"success": true, "message": "처리 완료"}
    public static CommonApiResponse<Void> ok(String message) {
        return CommonApiResponse.<Void>builder()
                .success(true)
                .message(message)
                .build();
    }
}

/*
 * [공통 응답 래퍼를 쓰는 이유]
 *
 * API 응답을 래퍼 없이 데이터만 반환하면 성공과 실패를 HTTP 상태 코드로만 구분하게 된다.
 * 200 OK 인데 데이터가 없는 경우, 또는 에러 응답 구조가 성공 응답과 달라서
 * 프론트엔드가 매 API 마다 다른 방식으로 처리해야 하는 문제가 생긴다.
 *
 * 공통 래퍼를 쓰면 프론트엔드는 항상 response.success 로 성공 여부를 확인하고
 * response.data 에서 실제 데이터를 꺼내는 동일한 패턴을 쓸 수 있다.
 *
 *
 * [제네릭 <T> 동작 이해]
 *
 * CommonApiResponse<StudyResponse> 라고 쓰면 data 필드의 타입이 StudyResponse 가 된다.
 * CommonApiResponse<Void> 라고 쓰면 data 필드가 Void 타입이 되어 항상 null 이 된다.
 * 컴파일러가 타입을 검사하므로 엉뚱한 데이터가 들어가는 실수를 방지한다.
 *
 *
 * [세 가지 ok() 메서드가 공존하는 이유]
 *
 * Java 는 메서드 오버로딩을 지원하므로 같은 이름의 메서드를 파라미터 타입/개수에 따라
 * 여러 개 정의할 수 있다. 호출할 때 파라미터에 맞는 메서드가 자동으로 선택된다.
 *
 *   ok(studyResponse)          → 데이터만 반환
 *   ok("생성됨", studyResponse) → 메시지 + 데이터 반환
 *   ok("처리 완료")             → 메시지만 반환
 *
 * 세 번째 메서드만 반환 타입이 CommonApiResponse<Void> 로 고정된다.
 * 따라서 컨트롤러의 반환 타입도 <Void> 로 맞춰야 컴파일 오류가 없다.
 *
 *
 * [@JsonInclude(NON_NULL) 의 실제 효과]
 *
 * ok("처리 완료") 로 만든 응답을 JSON 으로 변환하면:
 * NON_NULL 없이: {"success": true, "message": "처리 완료", "data": null}
 * NON_NULL 있으면: {"success": true, "message": "처리 완료"}
 *
 * null 필드가 응답에 포함되면 클라이언트가 불필요한 null 처리 코드를 추가해야 한다.
 */
