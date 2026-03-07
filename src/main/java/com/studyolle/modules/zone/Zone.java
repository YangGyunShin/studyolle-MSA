package com.studyolle.modules.zone;

import jakarta.persistence.*;
import lombok.*;

/**
 * ✅ 활동 지역 엔티티
 *
 * 시스템 전체에서 공유되는 지역 데이터를 표현하는 도메인 엔티티.
 * Account와 Study 모두 이 엔티티와 @ManyToMany 관계를 맺어
 * "사용자의 활동 지역"과 "스터디의 활동 지역"을 표현한다.
 *
 * Tag 엔티티와의 핵심 차이 — "닫힌 데이터":
 *   - Tag: 사용자가 자유롭게 입력하여 새로 생성 가능 (열린 데이터)
 *   - Zone: 시스템이 초기화 시 zones_kr.csv에서 일괄 로드 (닫힌 데이터)
 *   - 사용자는 미리 등록된 지역 중에서만 선택 가능 (프론트에서 enforceWhitelist: true)
 *   - 존재하지 않는 지역을 추가하려 하면 400 Bad Request 반환
 *
 * 연관관계 구조:
 *   - Account.zones (Set<Zone>) : 사용자가 관심 있는 활동 지역
 *   - Study.zones   (Set<Zone>) : 스터디가 활동하는 지역
 *   - 두 관계 모두 @ManyToMany이므로 조인 테이블(account_zones, study_zones)이 생성됨
 *
 * 데이터 형식 예시:
 *   - city: "Seoul"             (영문 도시명)
 *   - localNameOfCity: "서울"   (한글 도시명)
 *   - province: "서울특별시"     (시/도 단위)
 *   - toString(): "Seoul(서울)/서울특별시"
 *
 * @Table(uniqueConstraints) 설계:
 *   - city + province 복합 유니크 제약으로 동일 지역 중복 등록 방지
 *   - Tag는 단일 컬럼(title) unique였지만, Zone은 두 컬럼의 조합이 유일해야 함
 *   - 예: ("Seoul", "서울특별시")는 하나만 존재 가능
 *
 * Lombok 어노테이션:
 *   - @EqualsAndHashCode(of = "id"): Set<Zone>에서의 동등성 비교를 위해 id 기준
 *   - @Builder + @AllArgsConstructor: CSV 파싱 시 빌더 패턴으로 객체 생성
 *   - @NoArgsConstructor: JPA 스펙에서 요구하는 기본 생성자
 */
@Entity
@Getter
@Setter
@EqualsAndHashCode(of = "id")
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"city", "province"}))
public class Zone {

    @Id
    @GeneratedValue
    private Long id;

    /**
     * 영문 도시명 (예: "Seoul", "Busan", "Incheon")
     * - CSV 파일의 첫 번째 컬럼에서 로드
     * - 프론트엔드에서 지역 표시 시 toString()을 통해 한글명과 함께 출력
     */
    @Column(nullable = false)
    private String city;

    /**
     * 한글 도시명 (예: "서울", "부산", "인천")
     * - CSV 파일의 두 번째 컬럼에서 로드
     * - toString()에서 "Seoul(서울)" 형태로 괄호 안에 표시
     */
    @Column(nullable = false)
    private String localNameOfCity;

    /**
     * 시/도 단위 행정구역명 (예: "서울특별시", "경기도", "부산광역시")
     * - CSV 파일의 세 번째 컬럼에서 로드
     * - nullable = true: 일부 지역에서 province가 없을 수 있음
     * - ZoneRepository.findByCityAndProvince()에서 조회 키로 사용
     */
    @Column(nullable = true)
    private String province;

    /**
     * ✅ 프론트엔드 표시용 문자열 변환
     *
     * 형식: "Seoul(서울)/서울특별시"
     *
     * 이 형식은 Tagify UI에서 태그로 표시되며, ZoneForm에서 역파싱하여
     * city, localNameOfCity, province를 다시 추출하는 데 사용됨.
     *
     * 파싱 규칙 (ZoneForm에서):
     *   - city: "(" 앞 부분 → "Seoul"
     *   - localNameOfCity: "(" ~ ")" 사이 → "서울"
     *   - province: "/" 뒤 부분 → "서울특별시"
     */
    @Override
    public String toString() {
        return String.format("%s(%s)/%s", city, localNameOfCity, province);
    }
}