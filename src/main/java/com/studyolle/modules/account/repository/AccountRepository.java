// ✅ AccountRepository.java: 사용자 정보에 접근하는 JPA 인터페이스
package com.studyolle.modules.account.repository;

import com.studyolle.modules.account.entity.Account;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public interface AccountRepository extends JpaRepository<Account, Long>, QuerydslPredicateExecutor<Account> {

    // 이메일 중복 여부 체크
    boolean existsByEmail(String email);

    // 닉네임 중복 여부 체크
    boolean existsByNickname(String nickname);

    // 이메일 기반 사용자 조회
    Account findByEmail(String mail);

    // 닉네임 기반 사용자 조회
    Account findByNickname(String nickname);

    // ✅ 사용자 ID를 기준으로 사용자 정보를 조회하는 메서드
    // ✅ 연관된 관심 태그(Tag) 및 지역 정보(Zone)도 함께 로딩(Fetch Join)하도록 최적화
    //
    // 🔍 기본적으로 JPA는 @ManyToMany 관계인 tags/zones를 LAZY 로딩함
    //     → 따라서 Account를 조회한 후, getTags() 또는 getZones()를 호출하면 각각의 쿼리가 따로 발생함 (N+1 문제)
    //
    // 🔍 @EntityGraph는 JPQL 없이도 fetch join 효과를 내주는 Spring Data JPA의 기능
    //     → attributePaths 에 명시된 연관 필드(tags, zones)를 한 번의 쿼리로 함께 조회하도록 설정함
    //     → 성능 최적화 및 쿼리 수 감소 효과
    //
    // 📌 실제 SQL 예시:
    //     select a.*, t.*, z.*
    //     from account a
    //     left join account_tags at on a.id = at.account_id
    //     left join tag t on at.tag_id = t.id
    //     left join account_zones az on a.id = az.account_id
    //     left join zone z on az.zone_id = z.id
    //     where a.id = :id
    @EntityGraph(attributePaths = {"tags", "zones"})
    Account findAccountWithTagsAndZonesById(Long id);
}