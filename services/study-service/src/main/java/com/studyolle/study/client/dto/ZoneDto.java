package com.studyolle.study.client.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * metadata-service 로부터 받는 지역 정보 응답 DTO.
 *
 * =============================================
 * TagDto 와 동일한 설계 원칙
 * =============================================
 *
 * Zone 엔티티도 metadata-service 의 소유이므로
 * study-service 는 zoneIds(Long Set) 만 로컬에 저장하고,
 * 지역 이름 표시가 필요할 때 Feign 으로 조회한다.
 *
 * =============================================
 * 필드 구성
 * =============================================
 *
 * metadata-service 의 Zone 엔티티는 모노리틱의 Zone 엔티티와 동일한 구조를 따른다:
 * - city          : 영문 도시명 (예: "Seoul")
 * - localNameOfCity : 한글 도시명 (예: "서울")
 * - province      : 광역시/도 이름 (예: "서울특별시")
 *
 * =============================================
 * toDisplayString()
 * =============================================
 *
 * "Seoul(서울)/서울특별시" 형태의 문자열을 반환한다.
 * 모노리틱의 Zone.toString() 과 동일한 포맷이다.
 * Tagify 자동완성 목록에 표시되는 형태이며,
 * ZoneRequest 에서 파싱할 때 이 형태를 기준으로 cityName, provinceName 을 분리한다.
 */
@Getter
@NoArgsConstructor
public class ZoneDto {
    private Long id;
    private String city;
    private String localNameOfCity;
    private String province;

    /**
     * "Seoul(서울)/서울특별시" 형태의 표시용 문자열을 반환한다.
     *
     * 사용처:
     * - Tagify 자동완성 whitelist 문자열 생성
     * - 지역 설정 화면에서 선택된 지역 표시
     */
    public String toDisplayString() {
        return String.format("%s(%s)/%s", city, localNameOfCity, province);
    }
}