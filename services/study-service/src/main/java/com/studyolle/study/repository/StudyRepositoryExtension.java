package com.studyolle.study.repository;

import com.studyolle.study.entity.Study;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * StudyRepository 의 커스텀 쿼리 인터페이스.
 *
 * =============================================
 * 이 인터페이스가 필요한 이유
 * =============================================
 *
 * Spring Data JPA 의 메서드 이름 규칙(findBy~, existsBy~ 등)은 단순 조건에는 편리하지만
 * 다음과 같은 경우에는 한계가 있다:
 *
 * - 조건이 있을 수도 없을 수도 있는 동적 쿼리
 *   (예: "keyword 가 있으면 필터, recruiting 이 true 면 모집 중만, 없으면 전체")
 * - 여러 컬렉션을 IN 조건으로 동시에 검색하는 복합 쿼리
 * - LIMIT 과 ORDER BY 를 조합한 추천 쿼리
 *
 * 이런 복잡한 쿼리는 QueryDSL 로 직접 작성한다.
 * 구현 로직은 StudyRepositoryExtensionImpl 에 있다.
 *
 * =============================================
 * Spring Data JPA 커스텀 Repository 규칙
 * =============================================
 *
 * 1. StudyRepository 가 이 인터페이스를 상속받는다.
 * 2. 구현체 클래스 이름은 반드시 "StudyRepositoryExtensionImpl" 이어야 한다.
 *    Spring Data JPA 가 "상속 인터페이스명 + Impl" 규칙으로 구현체를 자동으로 감지한다.
 * 3. 구현체가 QuerydslRepositorySupport 를 상속받아 실제 쿼리 로직을 작성한다.
 *
 * 구조 요약:
 *   StudyRepository
 *     +-- extends JpaRepository<Study, Long>       (기본 CRUD)
 *     +-- extends StudyRepositoryExtension         (커스텀 쿼리 인터페이스)
 *                  +-- StudyRepositoryExtensionImpl (QueryDSL 구현체)
 */
@Transactional(readOnly = true)
public interface StudyRepositoryExtension {

    /**
     * 키워드 기반 스터디 검색 (페이징 지원).
     *
     * 검색 범위:
     * - study.title: 스터디 제목 부분 일치 (대소문자 무시)
     * - study.tagIds: 파라미터로 전달된 태그 ID 중 하나라도 포함된 스터디
     * - study.zoneIds: 파라미터로 전달된 지역 ID 중 하나라도 포함된 스터디
     *
     * 태그/지역 이름을 직접 검색하지 않고 ID 를 파라미터로 받는 이유:
     * Tag, Zone 엔티티는 metadata-service 가 소유하므로 study-service DB 에는 ID 만 저장되어 있다.
     * 컨트롤러(또는 서비스)가 먼저 metadata-service 에 keyword 를 보내 매칭되는 ID 목록을 받아온 뒤
     * 이 메서드에 전달한다.
     *
     * @param keyword    검색 키워드
     * @param tagIds     키워드와 이름이 매칭되는 태그 ID 목록 (없으면 빈 Set 또는 null)
     * @param zoneIds    키워드와 이름이 매칭되는 지역 ID 목록 (없으면 빈 Set 또는 null)
     * @param pageable   페이징 및 정렬 정보
     * @param recruiting true 이면 모집 중인 스터디만 필터링
     * @param open       true 이면 진행 중(closed=false)인 스터디만 필터링
     * @return 페이징된 검색 결과
     */
    Page<Study> findByKeyword(String keyword, Set<Long> tagIds, Set<Long> zoneIds,
                              Pageable pageable, boolean recruiting, boolean open);

    /**
     * 사용자 관심사(태그/지역 ID) 기반 스터디 추천.
     *
     * 추천 조건:
     * - published = true (공개 상태)
     * - closed = false (진행 중)
     * - study.tagIds 중 하나 이상이 파라미터 tagIds 에 포함
     * - study.zoneIds 중 하나 이상이 파라미터 zoneIds 에 포함
     *
     * 결과: 최신순 정렬, 최대 9개.
     *
     * @param tagIds  사용자의 관심 태그 ID 목록 (account-service 에서 조회)
     * @param zoneIds 사용자의 활동 지역 ID 목록 (account-service 에서 조회)
     * @return 추천 스터디 목록 (최대 9개, 최신순)
     */
    List<Study> findByAccount(Set<Long> tagIds, Set<Long> zoneIds);
}