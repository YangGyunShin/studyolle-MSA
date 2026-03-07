package com.studyolle.modules.event.entity;

import com.studyolle.modules.account.entity.Account;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 이벤트(모임) 참가 신청 엔티티
 *
 * - 사용자(Account)가 특정 이벤트(Event)에 참가 신청한 내역을 표현
 * - 신청 시각, 승인 여부, 출석 여부 등 참가 상태를 관리
 *
 * [엔티티 연관관계 구조]
 *
 *   Enrollment --> Event --> Study
 *   (참가 신청)    (모임)    (스터디)
 *
 *   예) "홍길동이 Java 스터디의 1월 정기모임에 참가 신청함"
 *       Enrollment(홍길동) --> Event(1월 정기모임) --> Study(Java 스터디)
 *
 *
 * [@NamedEntityGraph 설명]
 *
 * JPA는 기본적으로 연관 엔티티를 지연 로딩(LAZY)으로 처리한다.
 * 즉, Enrollment를 조회할 때 Event와 Study는 실제로 접근하는 시점에 추가 쿼리가 발생한다.
 *
 *   enrollment.getEvent();                  // --> DB 쿼리 1회 (Event 조회)
 *   enrollment.getEvent().getStudy();       // --> DB 쿼리 1회 (Study 조회)
 *
 * 참가 신청이 10건이면 최대 1 + 10 + 10 = 21번의 쿼리가 실행될 수 있다. (N+1 문제)
 *
 * @NamedEntityGraph는 이 문제를 해결하기 위해
 * "이 엔티티를 조회할 때 관련 엔티티도 한 번에 가져와라"고 JPA에게 미리 알려주는 설정이다.
 *
 *   @NamedEntityGraph(
 *       name = "Enrollment.withEventAndStudy",
 *          --> 이 설정의 이름표. Repository에서 @EntityGraph("Enrollment.withEventAndStudy")로 참조한다.
 *
 *       attributeNodes = @NamedAttributeNode(value = "event", subgraph = "study"),
 *          --> Enrollment의 event 필드를 함께 조회한다.
 *          --> subgraph = "study"는 "event 안에서 추가로 조회할 게 있다"는 의미로,
 *              아래 subgraphs에 정의된 "study" 설정을 참조한다.
 *
 *       subgraphs = @NamedSubgraph(name = "study", attributeNodes = @NamedAttributeNode("study"))
 *          --> Event 엔티티 내부의 study 필드까지 함께 조회하도록 지정한다.
 *          --> subgraph가 필요한 이유: attributeNodes는 바로 옆 필드(1단계)만 지정할 수 있는데,
 *              Enrollment -> Event -> Study는 2단계 깊이이므로 "Event 안에서의 추가 조회 설정"이 필요하다.
 *   )
 *
 * 결과적으로 Enrollment + Event + Study를 한 번의 쿼리로 가져온다:
 *
 *   SELECT e.*, ev.*, s.*
 *   FROM enrollment e
 *   JOIN event ev ON e.event_id = ev.id
 *   JOIN study s ON ev.study_id = s.id
 *
 * [실제 사용 예시 - Repository에서 이름표로 참조]
 *
 *   @EntityGraph("Enrollment.withEventAndStudy")
 *   List<Enrollment> findByAccount(Account account);
 */
@NamedEntityGraph(
        name = "Enrollment.withEventAndStudy",
        attributeNodes = {@NamedAttributeNode(value = "event", subgraph = "study")},
        subgraphs = @NamedSubgraph(name = "study", attributeNodes = @NamedAttributeNode("study"))
)
@Entity
@Getter
@Setter
@EqualsAndHashCode(of = "id")
public class Enrollment {

    @Id
    @GeneratedValue
    private Long id;

    /** 참가 신청한 이벤트(모임) - Enrollment : Event = N : 1 */
    @ManyToOne
    private Event event;

    /** 참가 신청한 사용자 - Enrollment : Account = N : 1 */
    @ManyToOne
    private Account account;

    /** 참가 신청 일시 */
    private LocalDateTime enrolledAt;

    /** 참가 승인 여부 (FCFS: 자동 승인 / CONFIRMATIVE: 관리자 수동 승인) */
    private boolean accepted;

    /** 출석 여부 (운영자가 수동으로 체크) */
    private boolean attended;
}