package com.studyolle.modules.tag;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.*;

/**
 * ✅ 관심 주제 태그 엔티티
 *
 * 시스템 전체에서 공유되는 태그 데이터를 표현하는 도메인 엔티티.
 * Account와 Study 모두 이 엔티티와 @ManyToMany 관계를 맺어
 * "사용자의 관심 태그"와 "스터디의 주제 태그"를 표현한다.
 *
 * 연관관계 구조:
 *   - Account.tags (Set&lt;Tag&gt;) : 사용자가 관심 있는 주제
 *   - Study.tags   (Set&lt;Tag&gt;) : 스터디가 다루는 주제
 *   - 두 관계 모두 @ManyToMany이므로 조인 테이블(account_tags, study_tags)이 생성됨
 *   - Tag 쪽에서는 mappedBy를 선언하지 않으므로, 연관관계의 주인은 Account/Study 측
 *
 * 공유 엔티티 특성:
 *   - 하나의 Tag("Spring")는 여러 Account와 여러 Study에서 동시에 참조될 수 있음
 *   - 따라서 특정 사용자/스터디에서 태그를 "삭제"할 때는 연관관계(조인 테이블)만 제거하고,
 *     Tag 엔티티 자체는 삭제하지 않음 (다른 곳에서 참조 중일 수 있으므로)
 *   - @Column(unique = true)로 동일 제목의 태그가 중복 생성되는 것을 방지
 *
 * Lombok 어노테이션 설계:
 *   - @Getter/@Setter: 필드 접근자 자동 생성
 *   - @NoArgsConstructor: JPA 스펙에서 요구하는 기본 생성자
 *   - @AllArgsConstructor + @Builder: 태그 생성 시 빌더 패턴 사용 가능
 *     → Tag.builder().title("Spring").build()
 *   - @EqualsAndHashCode(of = "id"): id 기반 동등성 비교
 *
 * @EqualsAndHashCode(of = "id") 설계 의도:
 *   - Account.tags, Study.tags가 Set&lt;Tag&gt;이므로 equals/hashCode가 정확해야 함
 *   - title 대신 id를 기준으로 사용하는 이유:
 *     (1) title이 변경될 가능성이 있을 때 Set 내부 해시 충돌 방지
 *     (2) 새로 생성된 Tag(@GeneratedValue 적용 전)는 id가 null이므로
 *         아직 저장 전 상태에서는 모든 새 Tag가 동일한 해시코드를 가짐 → persist 후에만 정확히 작동
 *     (3) JPA 표준 패턴에서 식별자 기반 동등성이 가장 안정적
 *   - Set.remove(tag) 호출 시 이 equals/hashCode가 사용되어
 *     조인 테이블에서 올바른 레코드를 DELETE함
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Tag {

    @Id
    @GeneratedValue
    private Long id;

    /**
     * 태그 제목 (예: "Spring", "JPA", "Docker")
     *
     * - unique = true: 동일한 태그명이 두 번 생성되는 것을 DB 레벨에서 방지
     *   → TagService.findOrCreateNew()에서 "조회 후 없으면 생성" 패턴으로 활용
     * - nullable = false: 태그는 반드시 제목이 있어야 함
     *
     * 프론트엔드(Tagify)에서 사용자가 입력한 문자열이 그대로 이 필드에 저장됨
     */
    @Column(unique = true, nullable = false)
    private String title;
}