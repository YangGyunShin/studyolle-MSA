package com.studyolle.frontend.admin.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;

/**
 * admin-service 의 PageResponse 와 동일한 구조의 frontend 측 미러 DTO.
 *
 * Spring Data JPA 의 Page<T> 를 직접 역직렬화할 수 없어 만든 우회책이다.
 * 관련 배경은 admin-service 의 PageResponse 주석을 참고.
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