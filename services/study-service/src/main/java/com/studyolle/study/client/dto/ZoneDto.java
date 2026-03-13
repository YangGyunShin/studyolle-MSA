package com.studyolle.study.client.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * metadata-service 의 Zone 응답 DTO
 *
 * Zone 도 Tag 와 동일하게 study-service 로컬에 없다.
 * 지역 ID만 study_zone_ids 컬렉션 테이블에 저장하고,
 * 표시가 필요한 경우 metadata-service 에서 조회한다.
 */
@Getter
@NoArgsConstructor
public class ZoneDto {
    private Long id;
    private String city;
    private String localNameOfCity;
    private String province;

    // "Seoul(서울)/서울특별시" 형태로 반환 — 모노리틱 Zone.toString() 과 동일
    public String toDisplayString() {
        return String.format("%s(%s)/%s", city, localNameOfCity, province);
    }
}