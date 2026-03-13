package com.studyolle.study.client.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * metadata-service 의 Tag 응답 DTO
 *
 * study-service 는 Tag 엔티티를 로컬에 갖지 않는다.
 * 태그 정보가 필요할 때 metadata-service 를 Feign 으로 호출하고,
 * 반환받은 ID만 study_tag_ids 컬렉션 테이블에 저장한다.
 */
@Getter
@NoArgsConstructor
public class TagDto {
    private Long id;
    private String title;
}