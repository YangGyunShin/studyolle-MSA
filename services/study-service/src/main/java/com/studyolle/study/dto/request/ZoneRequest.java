package com.studyolle.study.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 지역 추가/제거 요청 DTO.
// 클라이언트는 "Seoul(서울)/서울특별시" 형태의 문자열 하나를 전송한다.
// city 와 province 를 별도 필드로 받지 않고 하나의 문자열로 받는 이유:
// 프론트엔드의 Tagify 위젯이 지역을 이 형식으로 표시하고 그대로 전송하기 때문이다.
// 파싱은 서버(이 클래스의 메서드)에서 처리한다.
@Getter
@NoArgsConstructor
public class ZoneRequest {

    // "Seoul(서울)/서울특별시" 형태의 지역 표시 문자열
    // 형식: {영문도시명}({한글도시명})/{도/특별시}
    @NotBlank
    private String zoneName;

    // zoneName 에서 영문 도시명을 추출한다.
    // "Seoul(서울)/서울특별시" → "Seoul"
    // "(" 가 나오기 전까지가 city 이므로 indexOf("(") 로 위치를 찾아 앞부분만 자른다
    public String getCityName() {
        return zoneName.substring(0, zoneName.indexOf("("));
    }

    // zoneName 에서 도/특별시 이름을 추출한다.
    // "Seoul(서울)/서울특별시" → "서울특별시"
    // "/" 다음부터 끝까지가 province 이므로 indexOf("/") + 1 로 시작 위치를 구한다
    public String getProvinceName() {
        return zoneName.substring(zoneName.indexOf("/") + 1);
    }
}

/*
 * [substring 파싱 방법 이해하기]
 *
 * 입력값 예시: "Seoul(서울)/서울특별시"
 *
 * indexOf("(") 는 "(" 문자가 처음 등장하는 인덱스를 반환한다.
 * "Seoul(서울)/서울특별시" 에서 "(" 는 5번째 위치(index 5)에 있다.
 * substring(0, 5) 는 0번부터 5번 직전까지, 즉 "Seoul" 을 반환한다.
 *
 * indexOf("/") 는 "/" 문자가 처음 등장하는 인덱스를 반환한다.
 * "Seoul(서울)/서울특별시" 에서 "/" 는 index 10 에 있다.
 * substring(10 + 1) 은 11번부터 끝까지, 즉 "서울특별시" 를 반환한다.
 *
 *
 * [왜 city + province 를 별도 필드 대신 하나의 문자열로 받는가?]
 *
 * 설정 화면에서 Tagify 지역 선택 위젯은 "Seoul(서울)/서울특별시" 라는 하나의 문자열을
 * 선택된 값으로 다룬다. 프론트엔드가 이 문자열을 그대로 서버에 전송하는 것이 가장 단순하다.
 * 서버가 파싱을 책임지면 프론트엔드는 값을 그대로 전달하기만 하면 된다.
 *
 * 만약 city 와 province 를 별도 필드로 받으려면 프론트엔드가 문자열을 직접 파싱해서
 * 두 값으로 나눠 전송해야 하는데, 파싱 로직이 프론트엔드에 중복되는 셈이다.
 */
