package com.studyolle.modules.study.repository;

import com.studyolle.modules.study.entity.Study;
import com.studyolle.modules.tag.Tag;
import com.studyolle.modules.zone.Zone;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * StudyRepositoryExtension - StudyRepository의 사용자 정의 쿼리 인터페이스
 *
 * =============================================
 * Spring Data JPA 커스텀 Repository 패턴
 * =============================================
 *
 * Spring Data JPA의 쿼리 메서드 이름 규칙만으로는 표현할 수 없는
 * 복잡한 쿼리(다중 조건, 동적 쿼리, 복합 JOIN 등)를 위해 별도 인터페이스로 분리합니다.
 *
 * 동작 원리:
 * 1. StudyRepository가 이 인터페이스를 상속받음
 * 2. 구현체 이름은 반드시 "StudyRepositoryExtensionImpl"이어야 함
 *    (Spring Data JPA가 "상속 인터페이스명 + Impl" 규칙으로 구현체를 자동 감지)
 * 3. 구현체에서 QueryDSL을 사용하여 실제 쿼리 로직을 작성
 *
 * 구조:
 * StudyRepository
 *   +-- extends JpaRepository<Study, Long>          (기본 CRUD)
 *   +-- extends StudyRepositoryExtension            (커스텀 쿼리)
 *              +-- StudyRepositoryExtensionImpl      (QueryDSL 구현)
 */
@Transactional(readOnly = true)
public interface StudyRepositoryExtension {

    /**
     * 키워드 기반 스터디 검색 (페이징 지원).
     *
     * 검색 대상 필드:
     * - Study.title (스터디 제목)
     * - Study.tags.title (연관된 태그의 제목)
     * - Study.zones.localNameOfCity (연관된 지역의 도시명)
     *
     * 검색 조건: published = true (공개된 스터디만 검색 가능)
     *
     * @param keyword  검색 키워드 (대소문자 무시)
     * @param pageable 페이징 및 정렬 정보
     * @return 페이징된 검색 결과
     */
    Page<Study> findByKeyword(String keyword, Pageable pageable, boolean recruiting, boolean open);

    /**
     * 사용자 관심사(태그/지역) 기반 스터디 추천.
     *
     * 추천 조건:
     * - published = true (공개 상태)
     * - closed = false (진행 중)
     * - 스터디의 tags 중 하나 이상이 사용자의 tags와 일치
     * - 스터디의 zones 중 하나 이상이 사용자의 zones와 일치
     *
     * 결과: 최신순 정렬, 최대 9개
     *
     * @param tags  사용자의 관심 태그 Set
     * @param zones 사용자의 활동 지역 Set
     * @return 추천 스터디 목록 (최대 9개)
     */
    List<Study> findByAccount(Set<Tag> tags, Set<Zone> zones);
}