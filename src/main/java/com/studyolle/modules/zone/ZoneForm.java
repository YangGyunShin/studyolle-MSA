package com.studyolle.modules.zone;

import lombok.Data;

/**
 * ✅ 지역 추가/삭제 요청 시 JSON 데이터를 바인딩하고 파싱하는 DTO
 *
 * 프론트엔드(Tagify)에서 AJAX 요청으로 전송하는 JSON 본문을 수신한 뒤,
 * Zone.toString() 형식의 문자열을 역파싱하여 city, localNameOfCity, province를 추출한다.
 *
 * 요청 예시 (지역 추가):
 *   POST /settings/zones/add
 *   Content-Type: application/json
 *   Body: {"zoneName": "Seoul(서울)/서울특별시"}
 *
 * 파싱 규칙:
 *   zoneName = "Seoul(서울)/서울특별시"
 *   - getCityName()        → "Seoul"       ("(" 앞 부분)
 *   - getLocalNameOfCity() → "서울"         ("(" ~ ")" 사이)
 *   - getProvinceName()    → "서울특별시"    ("/" 뒤 부분)
 *
 * TagForm과의 차이:
 *   - TagForm: 단순한 문자열(tagTitle) 하나만 전달
 *   - ZoneForm: Zone.toString() 형식의 복합 문자열을 수신하여 3개 필드로 분해
 *   - Zone은 닫힌 데이터이므로 파싱된 값으로 DB 조회만 수행 (새로 생성하지 않음)
 *
 * 사용처:
 *   - TagZoneController: Account의 활동 지역 추가/삭제
 *   - StudySettingsController: Study의 활동 지역 추가/삭제
 */
@Data
public class ZoneForm {

    /**
     * 프론트엔드에서 전송한 지역 문자열 (Zone.toString() 형식)
     * 예: "Seoul(서울)/서울특별시"
     */
    private String zoneName;

    /**
     * 영문 도시명 추출
     * "Seoul(서울)/서울특별시" → "Seoul"
     *
     * 파싱: 문자열 시작 ~ 첫 번째 "(" 사이
     * ZoneRepository.findByCityAndProvince()의 첫 번째 인자로 사용
     */
    public String getCityName() {
        return zoneName.substring(0, zoneName.indexOf("("));
    }

    /**
     * 시/도 행정구역명 추출
     * "Seoul(서울)/서울특별시" → "서울특별시"
     *
     * 파싱: "/" 다음 ~ 문자열 끝
     * ZoneRepository.findByCityAndProvince()의 두 번째 인자로 사용
     */
    public String getProvinceName() {
        return zoneName.substring(zoneName.indexOf("/") + 1);
    }

    /**
     * 한글 도시명 추출
     * "Seoul(서울)/서울특별시" → "서울"
     *
     * 파싱: "(" 다음 ~ ")" 이전
     * 현재 컨트롤러에서는 직접 사용하지 않지만,
     * getZone()에서 Zone 빌더를 통해 엔티티 생성 시 활용 가능
     */
    public String getLocalNameOfCity() {
        return zoneName.substring(zoneName.indexOf("(") + 1, zoneName.indexOf(")"));
    }

    /**
     * ✅ 파싱된 정보로 Zone 엔티티 객체를 생성 (toEntity 패턴)
     *
     * 주의: 이 메서드로 생성된 Zone은 영속 상태가 아님 (id가 없는 Transient 상태)
     * 현재 프로젝트에서는 이 메서드를 사용하지 않고,
     * getCityName() + getProvinceName()으로 DB에서 직접 조회하는 방식을 사용
     *
     * @return 파싱된 값으로 구성된 Zone 객체 (비영속 상태)
     */
    public Zone getZone() {
        return Zone.builder()
                .city(this.getCityName())
                .localNameOfCity(this.getLocalNameOfCity())
                .province(this.getProvinceName())
                .build();
    }
}