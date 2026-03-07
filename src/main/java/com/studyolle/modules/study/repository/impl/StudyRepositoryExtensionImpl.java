package com.studyolle.modules.study.repository.impl;

import com.querydsl.core.QueryResults;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPQLQuery;
import com.studyolle.modules.account.entity.QAccount;
import com.studyolle.modules.study.entity.QStudy;
import com.studyolle.modules.study.entity.Study;
import com.studyolle.modules.study.repository.StudyRepositoryExtension;
import com.studyolle.modules.tag.QTag;
import com.studyolle.modules.tag.Tag;
import com.studyolle.modules.zone.QZone;
import com.studyolle.modules.zone.Zone;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.support.QuerydslRepositorySupport;

import java.util.List;
import java.util.Set;

/**
 * StudyRepositoryExtensionImpl - QueryDSL 기반 커스텀 쿼리 구현체
 *
 * =============================================
 * QuerydslRepositorySupport 상속 이유
 * =============================================
 *
 * QuerydslRepositorySupport는 Spring Data JPA가 제공하는 추상 클래스로,
 * 다음과 같은 유틸리티를 제공합니다:
 *
 * - from(QEntity): QueryDSL 쿼리 시작점 제공
 * - getQuerydsl(): Querydsl 유틸리티 객체 반환
 *   - applyPagination(pageable, query): Spring의 Pageable을 QueryDSL 쿼리에 적용
 *
 * 생성자에서 super(Study.class)를 호출하여 기본 엔티티 타입을 지정합니다.
 *
 * =============================================
 * N+1 방지를 위한 fetch join 전략
 * =============================================
 *
 * 두 쿼리 모두 leftJoin().fetchJoin()을 사용하여 연관 엔티티를 미리 로딩합니다.
 * 이렇게 하면 스터디 목록을 순회하며 각 스터디의 tags, zones, members에
 * 접근할 때 추가 쿼리가 발생하지 않습니다.
 *
 * leftJoin (LEFT OUTER JOIN)을 사용하는 이유:
 * - 태그나 지역이 없는 스터디도 결과에 포함시키기 위함
 * - INNER JOIN을 사용하면 연관 엔티티가 없는 스터디가 누락됨
 *
 * distinct()를 사용하는 이유:
 * - fetch join으로 인해 카테시안 곱이 발생하여 동일 스터디가 중복 조회될 수 있음
 * - distinct로 중복을 제거
 */
public class StudyRepositoryExtensionImpl extends QuerydslRepositorySupport implements StudyRepositoryExtension {

    public StudyRepositoryExtensionImpl() {
        super(Study.class);
    }

    /**
     * 키워드 기반 스터디 검색 + 페이징 처리
     *
     * =============================================
     * 쿼리 구조
     * =============================================
     *
     * WHERE 절:
     *   published = true
     *   AND (title LIKE '%keyword%'
     *        OR tags.title LIKE '%keyword%'
     *        OR zones.localNameOfCity LIKE '%keyword%')
     *
     * containsIgnoreCase: 대소문자 무시 + 부분 일치 검색
     * any(): 컬렉션(tags, zones) 내 하나라도 조건을 만족하면 true
     *
     * =============================================
     * 페이징 처리 흐름
     * =============================================
     *
     * 1. 기본 쿼리 구성 (WHERE, JOIN, DISTINCT)
     * 2. getQuerydsl().applyPagination()으로 Spring Pageable 적용
     *    - 내부적으로 OFFSET, LIMIT, ORDER BY 절을 쿼리에 추가
     * 3. fetchResults()로 데이터 + 전체 카운트를 한 번에 조회
     * 4. PageImpl로 Spring의 Page 객체 생성
     *
     * @param keyword  검색 키워드
     * @param pageable 페이징 정보 (page, size, sort)
     * @return 페이징된 검색 결과
     */
    @Override
    public Page<Study> findByKeyword(String keyword, Pageable pageable, boolean recruiting, boolean open) {
        QStudy study = QStudy.study;

        if (keyword == null || keyword.isBlank()) {
            return Page.empty(pageable);
        }

        // 기본 조건: 공개된 스터디 + 키워드 매칭
        BooleanExpression baseCondition = study.published.isTrue()
                .and(
                        study.title.containsIgnoreCase(keyword)
                                .or(study.tags.any().title.containsIgnoreCase(keyword))
                                .or(study.zones.any().localNameOfCity.containsIgnoreCase(keyword))
                );

        // 필터 조건 추가
        if (recruiting) {
            baseCondition = baseCondition.and(study.recruiting.isTrue());
        }
        if (open) {
            baseCondition = baseCondition.and(study.closed.isFalse());
        }

        JPQLQuery<Study> query = from(study)
                .where(baseCondition)
                .leftJoin(study.tags, QTag.tag).fetchJoin()
                .leftJoin(study.zones, QZone.zone).fetchJoin()
                .leftJoin(study.members, QAccount.account).fetchJoin()
                .distinct();

        JPQLQuery<Study> pageableQuery = getQuerydsl().applyPagination(pageable, query);
        QueryResults<Study> fetchResults = pageableQuery.fetchResults();

        return new PageImpl<>(fetchResults.getResults(), pageable, fetchResults.getTotal());
    }

    /**
     * 사용자 관심사 기반 스터디 추천 조회
     *
     * =============================================
     * 추천 알고리즘
     * =============================================
     *
     * 사용자의 프로필에 설정된 관심 태그(tags)와 활동 지역(zones)을 기반으로
     * 매칭되는 스터디를 찾습니다.
     *
     * 매칭 조건:
     * - 스터디의 tags 중 하나 이상이 사용자의 tags에 포함 (OR 매칭)
     * - 스터디의 zones 중 하나 이상이 사용자의 zones에 포함 (OR 매칭)
     * - 두 조건 모두 충족해야 함 (AND 결합)
     *
     * 필터 조건:
     * - published = true (공개된 스터디)
     * - closed = false (진행 중인 스터디)
     *
     * any().in(collection): 컬렉션 내 하나라도 주어진 Set에 포함되면 true
     *
     * @param tags  사용자의 관심 태그 Set
     * @param zones 사용자의 활동 지역 Set
     * @return 추천 스터디 목록 (최대 9개, 최신순)
     */
    @Override
    public List<Study> findByAccount(Set<Tag> tags, Set<Zone> zones) {
        QStudy study = QStudy.study;

        JPQLQuery<Study> query = from(study)
                .where(
                        study.published.isTrue()
                                .and(study.closed.isFalse())
                                .and(study.tags.any().in(tags))
                                .and(study.zones.any().in(zones))
                )
                .leftJoin(study.tags, QTag.tag).fetchJoin()
                .leftJoin(study.zones, QZone.zone).fetchJoin()
                .orderBy(study.publishedDateTime.desc())
                .distinct()
                .limit(9);

        return query.fetch();
    }
}