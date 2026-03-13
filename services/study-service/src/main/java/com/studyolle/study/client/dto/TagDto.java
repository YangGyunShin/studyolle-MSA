package com.studyolle.study.client.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * metadata-service 로부터 받는 태그 정보 응답 DTO.
 *
 * =============================================
 * 이 DTO 가 존재하는 이유
 * =============================================
 *
 * study-service 는 Tag 엔티티를 로컬 DB 에 갖지 않는다.
 * Tag 엔티티는 metadata-service 의 소유이므로,
 * study-service 가 태그 정보를 필요로 할 때는 반드시 Feign 으로 metadata-service 를 호출해야 한다.
 *
 * Feign Client 가 HTTP 응답 JSON 을 자바 객체로 역직렬화하기 위해 이 DTO 가 필요하다.
 * metadata-service 의 응답 JSON 구조가 { "id": 1, "title": "spring" } 라면
 * 이 클래스의 필드와 이름이 일치해야 Jackson 이 올바르게 매핑한다.
 *
 * =============================================
 * 사용 흐름
 * =============================================
 *
 * 1. MetadataFeignClient.findOrCreateTag("spring", "study-service") 호출
 * 2. metadata-service 가 { "id": 1, "title": "spring" } 을 JSON 으로 응답
 * 3. Feign + Jackson 이 이 JSON 을 TagDto 로 역직렬화
 * 4. studySettingsService.addTag() 에서 tagDto.getId() 를 꺼내 study.tagIds 에 추가
 *
 * @NoArgsConstructor: Jackson 이 JSON → 객체 역직렬화 시 기본 생성자가 필요하다.
 */
@Getter
@NoArgsConstructor
public class TagDto {
    private Long id;    // metadata-service DB 의 태그 PK
    private String title; // 태그 이름 (예: "spring", "java")
}