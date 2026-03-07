package com.studyolle.modules.notification;

import com.studyolle.modules.account.entity.Account;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 알림(Notification) 도메인 엔티티
 *
 * - 사용자에게 전달되는 알림 한 건을 표현
 * - 스터디 생성, 스터디 업데이트, 모임 참가 신청 등의 이벤트에 대해 알림이 생성됨
 *
 * =====================================================
 * [알림 생성 흐름]
 *
 * 이 엔티티는 직접 생성되지 않고, 이벤트 리스너(EventListener)에서 생성됨
 *
 *   1) 스터디 공개, 모임 등록 등의 도메인 이벤트 발생
 *   2) @EventListener가 해당 이벤트를 감지
 *   3) 알림 대상 사용자를 조회하여 Notification 엔티티 생성 및 저장
 *   4) 사용자가 알림 페이지에 접근하면 조회 후 읽음(checked) 처리
 *
 * =====================================================
 * [엔티티 동등성 비교]
 *
 * @EqualsAndHashCode(of = "id")
 *   -> id(PK) 값으로만 동등성을 비교
 *   -> JPA 엔티티에서 연관 관계 필드(account 등)를 포함하면
 *      지연 로딩 시 불필요한 쿼리 발생, 순환 참조 문제가 생길 수 있음
 *   -> 따라서 PK만으로 비교하는 것이 JPA 엔티티의 표준 관행
 */
@Entity
@Getter
@Setter
@EqualsAndHashCode(of = "id")
public class Notification {

    @Id
    @GeneratedValue
    private Long id;

    // 알림 제목 (예: "스터디올래 스터디가 공개되었습니다")
    private String title;

    // 알림 클릭 시 이동할 URL 링크 (예: "/study/studyolle")
    private String link;

    // 알림 본문 메시지 (상세 설명)
    private String message;

    // 읽음 여부 (false: 새 알림, true: 읽은 알림)
    // -> 사용자가 알림 페이지를 열면 조회된 알림들이 true로 전환됨
    private boolean checked;

    // 알림을 받는 사용자
    // -> 다대일 관계: 한 사용자가 여러 알림을 받을 수 있음
    // -> 기본 페치 전략: EAGER (ManyToOne 기본값)
    //    알림 조회 시 어떤 사용자의 알림인지 항상 필요하므로 EAGER가 적절
    @ManyToOne
    private Account account;

    // 알림 생성 시각
    private LocalDateTime createdDateTime;

    // 알림 유형 (스터디 생성 / 스터디 업데이트 / 모임 참가)
    // -> @Enumerated(EnumType.STRING): DB에 문자열("STUDY_CREATED")로 저장
    //    EnumType.ORDINAL(기본값)은 순서 번호(0, 1, 2)로 저장되어
    //    Enum 순서 변경 시 기존 데이터와 불일치 위험이 있음
    @Enumerated(EnumType.STRING)
    private NotificationType notificationType;
}