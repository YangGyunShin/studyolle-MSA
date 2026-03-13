package com.studyolle.study.repository;

import com.studyolle.study.entity.Study;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * StudyRepository 커스텀 쿼리 인터페이스
 *
 * =============================================
 * [모노리틱과의 변경사항]
 * =============================================
 *
 * [findByKeyword 파라미터 변경]
 * 모노리틱에서는 keyword 로 study.tags.title, study.zones.localNameOfCity 를
 * 직접 JOIN 해서 검색했으나, 길 1 에서는 Tag/Zone 엔티티가 없으므로:
 * 1. Controller 가 MetadataFeignClient.findTagIdsByKeyword(keyword) 를 먼저 호출
 * 2. 반환된 tagIds, zoneIds 를 파라미터로 전달
 * 3. QueryDSL 이 study.tagIds.any().in(tagIds) 로 필터링
 *
 * [findByAccount 파라미터 변경]
 * 모노리틱: findByAccount(Set<Tag> tags, Set<Zone> zones)
 * MSA 길 1: findByAccount(Set<Long> tagIds, Set<Long> zoneIds)
 * account-service 가 반환한 interestTagIds, activityZoneIds 를 그대로 사용한다.
 */
@Transactional(readOnly = true)
public interface StudyRepositoryExtension {

    /**
     * 키워드 기반 스터디 검색 (페이징 지원).
     *
     * [검색 범위]
     * - study.title: 스터디 제목 (LIKE)
     * - study.tagIds: metadata-service 에서 미리 조회한 매칭 태그 ID 목록
     * - study.zoneIds: metadata-service 에서 미리 조회한 매칭 지역 ID 목록
     *
     * @param keyword  검색 키워드
     * @param tagIds   키워드와 매칭되는 태그 ID 목록 (metadata-service 에서 조회)
     * @param zoneIds  키워드와 매칭되는 지역 ID 목록 (metadata-service 에서 조회)
     * @param pageable 페이징 및 정렬 정보
     * @param recruiting 모집 중 여부 필터
     * @param open     진행 중(미종료) 여부 필터
     */
    Page<Study> findByKeyword(String keyword, Set<Long> tagIds, Set<Long> zoneIds,
                              Pageable pageable, boolean recruiting, boolean open);

    /**
     * 사용자 관심사(태그/지역 ID) 기반 스터디 추천.
     *
     * [모노리틱 참조: StudyRepositoryExtensionImpl.findByAccount]
     * 모노리틱에서는 Set<Tag>, Set<Zone> 으로 직접 JOIN 했으나,
     * MSA 에서는 ID 비교로 동일한 결과를 낸다.
     *
     * 추천 조건:
     * - published = true (공개 상태)
     * - closed = false (진행 중)
     * - study.tagIds 중 하나 이상이 파라미터 tagIds 에 포함
     * - study.zoneIds 중 하나 이상이 파라미터 zoneIds 에 포함
     *
     * @param tagIds  사용자의 관심 태그 ID 목록 (account-service 에서 조회)
     * @param zoneIds 사용자의 활동 지역 ID 목록 (account-service 에서 조회)
     * @return 추천 스터디 목록 (최대 9개, 최신순)
     */
    List<Study> findByAccount(Set<Long> tagIds, Set<Long> zoneIds);
}