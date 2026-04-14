package com.studyolle.adminfrontend.member.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;

/**
 * admin-service 의 PageResponse 와 동일한 구조의 미러 DTO.
 *
 * Spring Data JPA 의 Page<T> 는 Jackson 이 직접 역직렬화하기 어렵다(PageImpl 의 기본 생성자가 없음).
 * 그래서 두 서비스 모두 이 우회용 단순 POJO 를 각자 들고 있다. 자세한 배경은 admin-service 의 PageResponse 주석을 참고.
 */
@Getter
public class AdminPageResponse<T> {

    private final List<T> content;
    private final long totalElements;
    private final int totalPages;
    private final int number;
    private final int size;

    @JsonCreator
    public AdminPageResponse(
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