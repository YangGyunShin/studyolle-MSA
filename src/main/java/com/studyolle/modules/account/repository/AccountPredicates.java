package com.studyolle.modules.account.repository;

import com.querydsl.core.types.Predicate;
import com.studyolle.modules.account.entity.QAccount;
import com.studyolle.modules.tag.Tag;
import com.studyolle.modules.zone.Zone;

import java.util.Set;

/**
 * ✅ Account 관련 QueryDSL Predicate(조건)들을 정의하는 헬퍼 클래스
 *
 * ──────────────────────────────────────────────────────────────────
 * [배경] QueryDSL이란?
 * ──────────────────────────────────────────────────────────────────
 *
 * SQL의 WHERE 조건을 자바 코드로 타입 안전하게 작성할 수 있게 해주는 라이브러리이다.
 *
 * 기존 방식들의 한계:
 *   1. 메서드 이름 기반 (Spring Data JPA 기본)
 *      → findByTagsInAndZonesInAndEmailVerifiedTrue(...)
 *      → 조건이 복잡해지면 메서드명이 비현실적으로 길어짐
 *
 *   2. JPQL 직접 작성 (@Query 어노테이션)
 *      → @Query("SELECT a FROM Account a JOIN a.zones z WHERE z IN :zones")
 *      → 문자열이라 컴파일 시점에 오류를 잡을 수 없음 (오타, 필드명 변경 시 런타임 에러)
 *
 *   3. QueryDSL (이 클래스가 사용하는 방식)
 *      → account.zones.any().in(zones)
 *      → 자바 코드이므로 컴파일 시점에 오류 검출 가능
 *      → IDE 자동완성 지원, 리팩토링에 안전
 *
 * ──────────────────────────────────────────────────────────────────
 * [핵심 개념] Q-클래스 (QAccount)
 * ──────────────────────────────────────────────────────────────────
 *
 * QueryDSL은 빌드 시점에 엔티티를 분석하여 Q-클래스를 자동 생성한다:
 *
 *   Account.java (개발자가 작성한 엔티티)
 *       ↓  QueryDSL 어노테이션 프로세서 (빌드 시 자동 실행)
 *   QAccount.java (자동 생성된 쿼리 전용 클래스)
 *
 * Account 엔티티의 각 필드에 대응하는 쿼리용 경로(Path) 객체가 만들어진다:
 *
 *   Account.java의 필드     →  QAccount.java의 경로 객체
 *   ─────────────────────       ──────────────────────────────
 *   private Long id         →  NumberPath<Long> id           // WHERE id = ?
 *   private String email    →  StringPath email              // WHERE email = ?
 *   private String nickname →  StringPath nickname           // WHERE nickname LIKE ?
 *   private Set<Tag> tags   →  SetPath<Tag, QTag> tags       // JOIN tags ...
 *   private Set<Zone> zones →  SetPath<Zone, QZone> zones    // JOIN zones ...
 *
 * 이 경로 객체들을 이용해 자바 코드로 SQL 조건을 표현할 수 있다:
 *   account.email.eq("test@email.com")     → WHERE email = 'test@email.com'
 *   account.nickname.contains("yang")      → WHERE nickname LIKE '%yang%'
 *   account.emailVerified.isTrue()         → WHERE email_verified = true
 *
 * ──────────────────────────────────────────────────────────────────
 * [핵심 개념] Predicate
 * ──────────────────────────────────────────────────────────────────
 *
 * Predicate는 QueryDSL이 만들어내는 "조건 객체"로, SQL의 WHERE절에 해당한다.
 * 여러 조건을 .and(), .or()로 조합하여 복잡한 쿼리를 구성할 수 있다.
 *
 * ──────────────────────────────────────────────────────────────────
 * [사용 방법] QuerydslPredicateExecutor
 * ──────────────────────────────────────────────────────────────────
 *
 * AccountRepository가 QuerydslPredicateExecutor<Account>를 상속하면
 * findAll(Predicate) 메서드를 사용할 수 있게 된다:
 *
 *   public interface AccountRepository extends JpaRepository<Account, Long>,
 *                                              QuerydslPredicateExecutor<Account> { }
 *
 * 사용 예시 (알림 발송 대상 조회):
 *   Predicate predicate = AccountPredicates.findByTagsAndZones(study.getTags(), study.getZones());
 *   Iterable<Account> accounts = accountRepository.findAll(predicate);
 *   // → 이 스터디의 태그/지역과 관심사가 겹치는 사용자들에게 알림 발송
 *
 * 즉, 스터디가 생성되거나 업데이트될 때 "이 스터디에 관심 있을 만한 사용자"를
 * 찾아서 알림을 보내는 기능의 핵심 조회 로직이다.
 */
public class AccountPredicates {

    /**
     * ✅ 관심 태그와 활동 지역이 겹치는 사용자를 찾기 위한 Predicate 생성
     *
     * 비즈니스 의미:
     *   "이 스터디의 태그/지역과 관심사가 하나라도 겹치는 사용자를 모두 찾아라"
     *
     * 조건 구조:
     *   (사용자의 zones 중 하나라도 전달된 zones에 포함) AND
     *   (사용자의 tags 중 하나라도 전달된 tags에 포함)
     *
     * 생성되는 SQL (개념적):
     *   SELECT a.*
     *   FROM account a
     *   WHERE EXISTS (
     *       SELECT 1 FROM account_zones az
     *       WHERE az.account_id = a.id
     *       AND az.zone_id IN (전달된 zone id들)
     *   )
     *   AND EXISTS (
     *       SELECT 1 FROM account_tags at
     *       WHERE at.account_id = a.id
     *       AND at.tag_id IN (전달된 tag id들)
     *   )
     *
     * @param tags   매칭할 관심 태그 집합 (예: Java, Spring 등)
     * @param zones  매칭할 활동 지역 집합 (예: 서울, 부산 등)
     * @return       위 조건을 만족하는 Predicate 객체
     */
    public static Predicate findByTagsAndZones(Set<Tag> tags, Set<Zone> zones) {

        // [1] QAccount 싱글턴 인스턴스 참조
        //     - QAccount 클래스 내부에 미리 생성된 정적 인스턴스
        //     - 이 객체를 통해 Account 엔티티의 모든 필드에 쿼리 경로로 접근 가능
        //     - 예: account.email, account.nickname, account.tags, account.zones 등
        QAccount account = QAccount.account;

        // [2] 지역(Zone) 매칭 조건
        //     account.zones      → Account 엔티티의 @ManyToMany Set<Zone> 필드에 대한 쿼리 경로
        //     .any()             → "컬렉션 중 하나라도" (SQL의 EXISTS 서브쿼리로 변환)
        //     .in(zones)         → "전달된 zones 집합에 포함되는지" (SQL의 IN 절로 변환)
        //
        //     생성되는 SQL:
        //       EXISTS (
        //           SELECT 1 FROM account_zones az
        //           JOIN zone z ON az.zone_id = z.id
        //           WHERE az.account_id = account.id
        //           AND z.id IN (?, ?, ?)       ← zones 파라미터의 각 Zone ID
        //       )

        // [3] 태그(Tag) 매칭 조건
        //     account.tags       → Account 엔티티의 @ManyToMany Set<Tag> 필드에 대한 쿼리 경로
        //     .any().in(tags)    → zones와 동일한 패턴
        //
        //     생성되는 SQL:
        //       EXISTS (
        //           SELECT 1 FROM account_tags at
        //           JOIN tag t ON at.tag_id = t.id
        //           WHERE at.account_id = account.id
        //           AND t.id IN (?, ?, ?)       ← tags 파라미터의 각 Tag ID
        //       )

        // [4] 두 조건을 AND로 결합하여 최종 Predicate 반환
        //     → "지역이 겹치는 사용자" 이면서 동시에 "태그가 겹치는 사용자"를 찾음
        //     → 이 Predicate를 accountRepository.findAll(predicate)에 전달하면
        //       조건에 맞는 Account 목록이 반환됨
        return account.zones.any().in(zones).and(account.tags.any().in(tags));
    }
}