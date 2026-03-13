package com.studyolle.study.repository.impl;

import com.querydsl.core.QueryResults;                              // 페이징용 - 결과 + 전체 count 를 함께 담는 래퍼
import com.querydsl.core.types.dsl.BooleanExpression;              // WHERE 조건 하나를 표현하는 타입 (and/or 로 조합 가능)
import com.querydsl.jpa.JPQLQuery;                                  // QueryDSL 이 생성하는 JPQL 쿼리 객체
import com.studyolle.study.entity.QStudy;                          // Study 엔티티에서 자동 생성된 Q타입 (쿼리용 메타모델)
import com.studyolle.study.entity.Study;
import com.studyolle.study.repository.StudyRepositoryExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;                    // Page 인터페이스의 기본 구현체
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.support.QuerydslRepositorySupport; // applyPagination() 유틸 제공

import java.util.List;
import java.util.Set;

/**
 * StudyRepositoryExtension 의 QueryDSL 구현체
 *
 * [QueryDSL 이란?]
 * Spring Data JPA 의 findBy~~ 메서드는 단순 조건에는 편리하지만,
 * 복잡한 동적 쿼리(조건이 있을 수도 없을 수도 있는 경우)에는 한계가 있다.
 * 예를 들어 "키워드가 있으면 제목으로 필터, 없으면 전체 조회"를 메서드 이름으로는 표현하기 어렵다.
 *
 * QueryDSL 은 Java 코드로 타입 안전하게 JPQL 을 작성할 수 있게 해주는 라이브러리다.
 * 컴파일 시점에 오류를 잡아주고, IDE 자동완성도 된다.
 *
 * [Q타입이란?]
 * build.gradle 의 annotationProcessor 설정에 의해 컴파일 시점에 엔티티 클래스를 분석해
 * QStudy, QJoinRequest 같은 "쿼리 전용 클래스"를 자동으로 생성한다.
 * QStudy.study.title, QStudy.study.published 처럼 필드를 타입 안전하게 참조할 수 있다.
 *
 * [QuerydslRepositorySupport 를 상속하는 이유]
 * getQuerydsl().applyPagination(pageable, query) 메서드를 쓰기 위함이다.
 * 이 메서드가 Pageable 의 page, size, sort 정보를 쿼리에 자동으로 붙여준다.
 * (OFFSET, LIMIT, ORDER BY 를 직접 계산하지 않아도 된다)
 */
public class StudyRepositoryExtensionImpl
        extends QuerydslRepositorySupport           // from(), getQuerydsl() 등 유틸 메서드를 물려받음
        implements StudyRepositoryExtension {

    public StudyRepositoryExtensionImpl() {
        super(Study.class);                         // 부모 생성자에 이 구현체가 다루는 엔티티 타입을 알려준다
    }

    /**
     * 키워드 기반 스터디 검색 + 페이징
     */
    @Override
    public Page<Study> findByKeyword(String keyword, Set<Long> tagIds, Set<Long> zoneIds,
                                     Pageable pageable, boolean recruiting, boolean open) {

        QStudy study = QStudy.study;                // Q타입 싱글턴 인스턴스. study.title, study.published 처럼 필드에 접근

        if (keyword == null || keyword.isBlank()) { // 키워드가 없으면 쿼리 실행 없이 빈 페이지 반환
            return Page.empty(pageable);
        }

        // -------------------------------------------------------
        // WHERE 조건 조립 — BooleanExpression 을 and/or 로 연결
        // -------------------------------------------------------

        // study.title.containsIgnoreCase(keyword)
        // → JPQL: LOWER(s.title) LIKE LOWER('%keyword%')
        // 제목에 키워드가 포함되는지 대소문자 무시하고 검색
        BooleanExpression keywordCondition = study.title.containsIgnoreCase(keyword);

        // tagIds 가 있으면 OR 조건 추가
        // study.tagIds.any().in(tagIds)
        // → JPQL: EXISTS (SELECT 1 FROM study_tag_ids WHERE study_id = s.id AND tag_id IN (:tagIds))
        // study 의 tagIds 컬렉션 중 하나라도 파라미터 tagIds 안에 있으면 매칭
        // tagIds 가 비어있으면 IN () 가 되어 SQL 오류가 나므로 반드시 isEmpty() 로 가드
        if (tagIds != null && !tagIds.isEmpty()) {
            keywordCondition = keywordCondition.or(study.tagIds.any().in(tagIds));
        }

        // zoneIds 도 동일한 패턴으로 OR 조건 추가
        // study.zoneIds.any().in(zoneIds)
        // → EXISTS (SELECT 1 FROM study_zone_ids WHERE study_id = s.id AND zone_id IN (:zoneIds))
        if (zoneIds != null && !zoneIds.isEmpty()) {
            keywordCondition = keywordCondition.or(study.zoneIds.any().in(zoneIds));
        }

        // 기본 조건: 공개된 스터디 AND (제목매칭 OR 태그매칭 OR 지역매칭)
        // study.published.isTrue() → WHERE s.published = true
        BooleanExpression baseCondition = study.published.isTrue().and(keywordCondition);

        // recruiting = true 파라미터가 넘어오면 모집 중인 스터디만 필터
        // study.recruiting.isTrue() → AND s.recruiting = true
        if (recruiting) {
            baseCondition = baseCondition.and(study.recruiting.isTrue());
        }

        // open = true 파라미터가 넘어오면 종료되지 않은 스터디만 필터
        // study.closed.isFalse() → AND s.closed = false
        if (open) {
            baseCondition = baseCondition.and(study.closed.isFalse());
        }

        // -------------------------------------------------------
        // 쿼리 실행
        // -------------------------------------------------------

        // from(study) → QuerydslRepositorySupport 가 EntityManager 를 주입해 쿼리를 시작
        // .where(baseCondition) → 위에서 조립한 WHERE 조건 적용
        JPQLQuery<Study> query = from(study).where(baseCondition);

        // getQuerydsl().applyPagination(pageable, query)
        // → Pageable 의 page, size, sort 를 쿼리에 자동 적용
        // → OFFSET (page * size), LIMIT (size), ORDER BY sort.property 가 붙는다
        JPQLQuery<Study> pageableQuery = getQuerydsl().applyPagination(pageable, query);

        // fetchResults() → SELECT 쿼리 + COUNT 쿼리를 함께 실행
        // results.getResults() → 현재 페이지의 Study 목록
        // results.getTotal()   → 조건에 맞는 전체 Study 개수 (페이지 계산용)
        QueryResults<Study> fetchResults = pageableQuery.fetchResults();

        // PageImpl(내용, pageable, 전체개수) → Page 인터페이스를 구현한 응답 객체 반환
        // 컨트롤러에서 Page.getTotalPages(), Page.getTotalElements() 등으로 활용 가능
        return new PageImpl<>(fetchResults.getResults(), pageable, fetchResults.getTotal());
    }

    /**
     * 사용자 관심사(태그/지역 ID) 기반 스터디 추천
     */
    @Override
    public List<Study> findByAccount(Set<Long> tagIds, Set<Long> zoneIds) {

        QStudy study = QStudy.study;                // Q타입 인스턴스 획득

        // 기본 조건: 공개 AND 진행 중
        // study.published.isTrue()  → WHERE s.published = true
        // study.closed.isFalse()    → AND s.closed = false
        BooleanExpression condition = study.published.isTrue().and(study.closed.isFalse());

        // 사용자 관심 태그와 겹치는 스터디 조건 추가
        // tagIds 가 비어있으면 조건 생략 (IN () SQL 오류 방지)
        if (tagIds != null && !tagIds.isEmpty()) {
            condition = condition.and(study.tagIds.any().in(tagIds));
        }

        // 사용자 활동 지역과 겹치는 스터디 조건 추가
        if (zoneIds != null && !zoneIds.isEmpty()) {
            condition = condition.and(study.zoneIds.any().in(zoneIds));
        }

        return from(study)
                .where(condition)                                        // WHERE 조건 적용
                .orderBy(study.publishedDateTime.desc())                 // ORDER BY s.published_date_time DESC (최신순)
                .limit(9)                                                // LIMIT 9 (추천 최대 9개)
                .fetch();                                                // SELECT 실행 후 List<Study> 반환
    }
}

/*
 * ============================================================
 * [QueryDSL 동작 원리 심층 설명]
 * ============================================================
 *
 * 1. Q타입이 만들어지는 과정
 * ------------------------------------------------------------
 * build.gradle 에 아래 설정이 있다:
 *
 *   annotationProcessor "com.querydsl:querydsl-apt:${querydslVersion}:jakarta"
 *
 * Gradle 이 compileJava 를 실행할 때 querydsl-apt 가 @Entity 가 붙은 클래스를 스캔해서
 * build/generated/sources/annotationProcessor/.../QStudy.java 를 자동 생성한다.
 *
 * QStudy 안에는 Study 의 모든 필드가 QueryDSL 의 Path 타입으로 변환되어 있다:
 *
 *   public class QStudy extends EntityPathBase<Study> {
 *       public static final QStudy study = new QStudy("study"); // 싱글턴 인스턴스
 *       public final StringPath title = createString("title");  // String 필드
 *       public final BooleanPath published = createBoolean("published"); // boolean 필드
 *       public final SetPath<Long, NumberPath<Long>> tagIds = ...; // @ElementCollection
 *   }
 *
 * study.title.containsIgnoreCase("spring") 이라고 쓸 수 있는 이유가 바로 이 때문이다.
 * 오타가 나도 컴파일 오류가 나서 런타임에 쿼리 오류가 날 일이 없다.
 *
 *
 * 2. BooleanExpression 조합 원리
 * ------------------------------------------------------------
 * BooleanExpression 은 SQL 의 WHERE 절 조건 하나를 자바 객체로 표현한 것이다.
 *
 *   BooleanExpression a = study.title.containsIgnoreCase("spring");
 *   // → LOWER(title) LIKE '%spring%'
 *
 *   BooleanExpression b = study.tagIds.any().in(Set.of(1L, 2L));
 *   // → EXISTS (SELECT 1 FROM study_tag_ids WHERE study_id = s.id AND tag_id IN (1, 2))
 *
 *   BooleanExpression ab = a.or(b);
 *   // → (LOWER(title) LIKE '%spring%' OR EXISTS (...))
 *
 *   BooleanExpression final = study.published.isTrue().and(ab);
 *   // → s.published = true AND (LOWER(title) LIKE '%spring%' OR EXISTS (...))
 *
 * 이처럼 조건을 변수에 담고 and/or 로 연결하는 방식 덕분에
 * "조건이 있을 때만 붙이고, 없으면 생략"하는 동적 쿼리가 가능하다.
 * if 문으로 조건을 추가하거나 생략할 수 있기 때문이다.
 *
 * JPQL 이나 @Query 를 직접 쓰면 동적 쿼리를 만들기 위해
 * 문자열 + 조건문 조합이 필요해서 코드가 복잡해지고 버그가 생기기 쉽다.
 *
 *
 * 3. @ElementCollection 과 any().in() 의 작동 방식
 * ------------------------------------------------------------
 * Study.tagIds 는 @ElementCollection 으로 선언된 Set<Long> 이다.
 * 이 컬렉션은 study_tag_ids 라는 별도 테이블에 저장된다:
 *
 *   study_tag_ids (study_id BIGINT, tag_id BIGINT)
 *
 * QueryDSL 에서 study.tagIds.any().in(tagIds) 를 쓰면
 * 아래와 같은 EXISTS 서브쿼리가 자동으로 생성된다:
 *
 *   EXISTS (
 *       SELECT 1
 *       FROM study_tag_ids sti
 *       WHERE sti.study_id = s.id
 *         AND sti.tag_id IN (1, 2, 3)
 *   )
 *
 * 즉, "이 스터디의 태그들 중 하나라도 사용자 관심 태그에 포함되어 있는가?"를 묻는 것이다.
 * any() 는 "컬렉션의 원소 중 하나라도" 라는 의미이고,
 * in(tagIds) 는 "파라미터 Set 안에 포함되는지"를 확인한다.
 *
 * 주의: tagIds 가 빈 Set 이면 IN () 가 되어 SQL 문법 오류가 발생한다.
 * 반드시 isEmpty() 로 가드해야 한다.
 *
 *
 * 4. fetchResults() vs fetch()
 * ------------------------------------------------------------
 * fetch()         → SELECT 만 실행. List<Study> 반환. 페이징 불필요할 때.
 * fetchResults()  → SELECT + COUNT 쿼리 두 번 실행.
 *                   전체 개수가 필요한 페이징에 사용.
 *                   results.getResults() → 현재 페이지 목록
 *                   results.getTotal()   → 전체 개수 (총 페이지 수 계산용)
 *
 * PageImpl 생성 시 total 이 필요하기 때문에 findByKeyword 에서는 fetchResults() 를 쓰고,
 * 추천 목록처럼 단순 List 반환인 findByAccount 에서는 fetch() 를 쓴다.
 *
 * 참고: QueryDSL 5.x 에서 fetchResults() 가 deprecated 되었지만
 * Spring Data JPA + QuerydslRepositorySupport 조합에서는 여전히 안정적으로 동작한다.
 * 완전히 교체하려면 fetchCount() 를 별도로 호출하는 방식으로 바꿀 수 있다.
 *
 *
 * 5. QuerydslRepositorySupport 의 from() 이 하는 일
 * ------------------------------------------------------------
 * from(study) 는 내부적으로 아래와 같은 일을 한다:
 *
 *   JPAQuery<Study> query = new JPAQuery<>(entityManager);
 *   query.from(study);
 *
 * QuerydslRepositorySupport 가 EntityManager 를 @PersistenceContext 로 주입받아서
 * 보관하고 있기 때문에, 하위 클래스에서는 entityManager 를 직접 다루지 않아도 된다.
 * Spring 의 트랜잭션 컨텍스트 안에서 실행된다.
 *
 *
 * 6. 모노리틱과 이 구현체의 차이 요약
 * ------------------------------------------------------------
 * 모노리틱에서는 Study 가 Tag, Zone 엔티티를 @ManyToMany 로 직접 참조했기 때문에
 * JOIN 을 직접 걸어서 태그/지역 이름으로 검색할 수 있었다:
 *
 *   .leftJoin(study.tags, QTag.tag).fetchJoin()
 *   .where(study.tags.any().title.containsIgnoreCase(keyword))
 *
 * MSA 길 1 에서는 Tag/Zone 엔티티가 metadata-service 소유이므로
 * study-service DB 에는 ID 만 있다. 그래서:
 *
 *   1단계: metadata-service 에 keyword 를 보내서 매칭되는 tagId 목록을 받아온다
 *   2단계: 받은 tagId 목록으로 study.tagIds.any().in(tagIds) 조건을 만든다
 *
 * 직접 JOIN 대신 EXISTS 서브쿼리를 쓰는 이유도 여기에 있다.
 * @ManyToMany 였을 때는 JOIN 이 가능했지만,
 * @ElementCollection Set<Long> 은 엔티티가 아니므로 fetch join 이 불가능하고
 * IN/EXISTS 로 처리해야 한다.
 */