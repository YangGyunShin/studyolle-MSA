package com.studyolle.modules.zone;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * ✅ Zone 엔티티 전용 데이터 접근 계층 (Spring Data JPA Repository)
 *
 * JpaRepository<Zone, Long>을 상속하여 기본 CRUD 메서드를 자동으로 제공받는다.
 * (save, saveAll, findById, findAll, count, delete 등)
 *
 * TagRepository와의 차이:
 *   - TagRepository: 단일 필드(title) 기반 조회 — findByTitle()
 *   - ZoneRepository: 복합 필드(city + province) 기반 조회 — findByCityAndProvince()
 *   - Zone은 @Table(uniqueConstraints)로 복합 유니크 제약이 설정되어 있으므로
 *     조회도 두 필드 조합으로 수행
 *
 * @Transactional(readOnly = true) 설계:
 *   - 인터페이스 레벨에 선언하여 모든 메서드에 기본적으로 읽기 전용 트랜잭션 적용
 *   - 읽기 전용 트랜잭션의 이점:
 *     (1) JPA Dirty Checking을 수행하지 않아 flush 시점의 비교 연산 생략 → 성능 향상
 *     (2) DB에 따라 읽기 전용 힌트를 전달하여 쿼리 최적화 가능
 *     (3) 의도치 않은 데이터 변경 방지 (안전장치)
 *   - 쓰기 연산(saveAll 등)은 ZoneService의 @Transactional이 우선 적용됨
 *
 * 사용처:
 *   - ZoneService: 초기화(initZoneData), 조회(findByCityAndProvince, getAllZoneNames)
 */
@Transactional(readOnly = true)
public interface ZoneRepository extends JpaRepository<Zone, Long> {

    /**
     * 영문 도시명(city) + 시/도명(province) 복합 조건으로 Zone 엔티티 조회
     *
     * Spring Data JPA 메서드 이름 기반 쿼리 파생:
     *   findBy + City + And + Province
     *   → SELECT z FROM Zone z WHERE z.city = :city AND z.province = :province
     *
     * 사용 예:
     *   zoneRepository.findByCityAndProvince("Seoul", "서울특별시") → Zone 객체
     *
     * 반환값:
     *   - 해당 조건의 Zone이 존재하면 Zone 객체 반환
     *   - 존재하지 않으면 null 반환
     *     → 호출 측(ZoneService)에서 null 체크 후 400 Bad Request 반환
     *
     * Zone은 닫힌 데이터이므로:
     *   - null이면 "존재하지 않는 지역" → 잘못된 요청
     *   - Tag처럼 "없으면 새로 생성"하지 않음
     *
     * @param city     영문 도시명 (예: "Seoul")
     * @param province 시/도 행정구역명 (예: "서울특별시")
     * @return 일치하는 Zone 엔티티, 없으면 null
     */
    Zone findByCityAndProvince(String city, String province);
}