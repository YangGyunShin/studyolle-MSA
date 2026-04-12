package com.studyolle.admin.client.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;

/**
 * Spring Data JPA 의 Page<T> 를 Feign 으로 역직렬화하기 위한 전용 DTO.
 *
 * [문제 — 왜 Page 를 직접 받을 수 없는가]
 * Spring 은 Page<T> 를 JSON 으로 직렬화할 때 아래 구조로 내보낸다:
 *   {
 *     "content": [...],
 *     "totalElements": 123,
 *     "totalPages": 7,
 *     "number": 0,
 *     "size": 20,
 *     "first": true,
 *     "last": false,
 *     "empty": false,
 *     "numberOfElements": 20,
 *     "pageable": { ... },
 *     "sort": { ... }
 *   }
 *
 * 그러나 역직렬화할 때는 Jackson 이 Spring 의 PageImpl 클래스 생성자 시그니처를 몰라서
 * "Cannot construct instance of PageImpl: no suitable constructor" 에러를 낸다.
 * PageImpl 의 생성자가 Jackson 이 기대하는 형식이 아니기 때문이다.
 *
 * [해결 — 필요한 필드만 가진 단순 POJO]
 * Spring 의 클래스를 피하고 우리가 필요한 필드만 받는 DTO 를 직접 만든다.
 * 목록과 페이지 메타데이터만 있으면 충분하므로 나머지 필드는 모두 생략한다.
 *
 * [@JsonCreator + final 필드 = 불변 객체]
 * 생성자로만 값을 채우고, 이후에는 변경할 수 없는 불변 객체를 만드는 패턴이다.
 * Jackson 은 @JsonCreator 가 붙은 생성자를 찾아서 사용하고, @JsonProperty 로
 * 각 파라미터가 JSON 의 어느 키에 해당하는지 명시한다. Setter 가 없어도 역직렬화가 된다.
 *
 * [T 제네릭으로 설계한 이유]
 * 회원, 스터디, 태그 등 여러 종류의 페이지 응답에 재사용할 수 있도록 요소 타입을 제네릭으로 두었다.
 */
@Getter
public class PageResponse<T> {

    private final List<T> content;
    private final long totalElements;
    private final int totalPages;
    private final int number;   // 현재 페이지 번호 (0 base)
    private final int size;     // 페이지 크기

    @JsonCreator
    public PageResponse(
            @JsonProperty("content") List<T> content,
            @JsonProperty("totalElements") long totalElements,
            @JsonProperty("totalPages") int totalPages,
            @JsonProperty("number") int number,
            @JsonProperty("size") int size) {
        this.content = content;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.number = number;
        this.size = size;
    }
}