package com.studyolle.study.entity;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// 스터디 가입 신청 엔티티.
// 가입 방식이 APPROVAL_REQUIRED 인 스터디에 가입 신청이 들어오면 이 레코드가 생성된다.
// 관리자가 승인하면 Study.memberIds 에 추가되고, 거절하면 그대로 REJECTED 상태로 남는다.
//
// @EqualsAndHashCode(of = "id"):
// JoinRequest 를 Set 이나 List 에 담을 때 id 기반으로 동등성을 비교한다.
// 기본 equals/hashCode 는 객체 참조를 비교하므로 같은 신청이라도 다른 객체이면 다르다고 판단한다.
// id 기반으로 재정의하면 DB 에서 조회한 같은 신청은 항상 동일하게 취급된다.
@Entity
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class JoinRequest {

    @Id
    @GeneratedValue // 값을 직접 지정하지 않으면 JPA 가 DB 시퀀스/auto_increment 로 자동 생성
    private Long id;

    // 신청자의 계정 ID. account-service 가 발급한 Account.id 값을 저장한다.
    // 엔티티 참조(@ManyToOne Account) 대신 Long ID 를 저장하는 이유:
    // account-service 는 별도의 DB 서버를 사용하므로 FK 제약을 걸 수 없다.
    // ID 값만 저장하고 필요할 때 account-service 에 HTTP 요청으로 정보를 조회한다.
    private Long accountId;

    // 신청 시점에 저장한 신청자 닉네임.
    // 신청 목록을 보여줄 때 매번 account-service 를 호출하지 않기 위해
    // 신청 당시의 닉네임을 여기에 복사해 저장한다(비정규화).
    // 이후 닉네임이 변경되어도 이 값은 "신청 당시 닉네임" 으로 유지된다.
    private String accountNickname;

    // 어떤 스터디에 대한 신청인지 연결.
    // FetchType.LAZY: JoinRequest 를 조회할 때 Study 는 즉시 불러오지 않고,
    // study 필드에 실제로 접근하는 시점에 SELECT 쿼리를 추가로 실행한다.
    // 신청 목록을 조회할 때 Study 정보까지 항상 필요한 것은 아니므로 LAZY 가 적합하다.
    @ManyToOne(fetch = FetchType.LAZY)
    private Study study;

    // 신청 처리 상태. DB 에 "PENDING", "APPROVED", "REJECTED" 문자열로 저장된다.
    // EnumType.ORDINAL(기본값) 은 순서 번호를 저장하므로
    // enum 에 값 추가/순서 변경 시 기존 데이터가 깨질 위험이 있다.
    // STRING 을 명시해서 항상 이름으로 저장하도록 강제한다.
    @Enumerated(EnumType.STRING)
    private JoinRequestStatus status;

    // 신청 접수 시각
    private LocalDateTime requestedAt;

    // 승인 또는 거절 처리 시각. 아직 PENDING 상태이면 null 이다.
    private LocalDateTime processedAt;

    // 가입 신청 객체를 생성하는 정적 팩터리 메서드.
    // new JoinRequest() 로 직접 생성하면 status, requestedAt 을 빠뜨릴 수 있다.
    // 팩터리 메서드를 쓰면 "초기 상태는 항상 PENDING, 신청 시각은 지금" 이라는
    // 규칙을 강제할 수 있다. 호출하는 쪽에서 실수할 여지가 없어진다.
    public static JoinRequest createRequest(Long accountId, String accountNickname, Study study) {
        JoinRequest request = new JoinRequest();
        request.setAccountId(accountId);
        request.setAccountNickname(accountNickname);
        request.setStudy(study);
        request.setStatus(JoinRequestStatus.PENDING); // 신청은 항상 대기 상태로 시작
        request.setRequestedAt(LocalDateTime.now());  // 신청 시각 = 현재 시각
        return request;
    }

    // 가입 신청을 승인한다.
    // 이 메서드는 상태만 바꾼다. Study.addMember() 호출은 서비스(StudyService)가 담당한다.
    // 상태 변경과 멤버 추가를 엔티티 안에 함께 두지 않는 이유:
    // Study 와 JoinRequest 두 엔티티가 서로를 제어하면 결합도가 높아진다.
    // 엔티티는 자기 자신의 상태만 바꾸고, 두 엔티티를 함께 조작하는 것은 서비스가 조율한다.
    public void approve() {
        this.status = JoinRequestStatus.APPROVED;
        this.processedAt = LocalDateTime.now();
    }

    // 가입 신청을 거절한다.
    public void reject() {
        this.status = JoinRequestStatus.REJECTED;
        this.processedAt = LocalDateTime.now();
    }

    // 현재 PENDING 상태인지 확인한다.
    // 이미 처리된(APPROVED/REJECTED) 신청을 다시 처리하려는 시도를 막기 위해 사용한다.
    public boolean isPending() {
        return this.status == JoinRequestStatus.PENDING;
    }
}

/*
 * [@ManyToOne 과 FetchType 이해하기]
 *
 * JoinRequest 는 하나의 Study 에 속한다(다대일 관계).
 * @ManyToOne 은 이 관계를 DB 의 FK(study_id)로 표현한다.
 *
 * FetchType 에는 두 가지가 있다:
 *
 * EAGER(즉시 로딩): JoinRequest 를 SELECT 할 때 Study 도 함께 JOIN 해서 가져온다.
 *   SELECT * FROM join_request jr JOIN study s ON jr.study_id = s.id WHERE jr.id = ?
 *   Study 정보가 항상 필요한 경우 쿼리 횟수가 줄어들지만,
 *   필요 없을 때도 무조건 JOIN 이 발생해 성능이 낭비된다.
 *
 * LAZY(지연 로딩): JoinRequest 만 SELECT 하고, study 필드에 실제로 접근할 때 추가 SELECT 한다.
 *   SELECT * FROM join_request WHERE id = ?   -- JoinRequest 조회
 *   SELECT * FROM study WHERE id = ?           -- request.getStudy() 호출 시 추가 실행
 *   필요할 때만 쿼리가 실행되므로 기본적으로 LAZY 를 권장한다.
 *
 * @ManyToOne 은 기본값이 EAGER 이므로 LAZY 를 명시적으로 선언해야 한다.
 * @OneToMany 는 기본값이 LAZY 다.
 *
 *
 * [Dirty Checking(변경 감지)이란?]
 *
 * JPA 트랜잭션 안에서 Repository 로 조회한 엔티티는 "영속 상태(Managed State)" 가 된다.
 * 영속 상태의 엔티티 필드를 변경하면, 트랜잭션이 커밋될 때
 * JPA 가 변경된 필드를 자동으로 감지해서 UPDATE 쿼리를 실행한다.
 * approve(), reject() 에서 명시적으로 repository.save() 를 호출하지 않아도
 * DB 에 변경이 반영되는 이유가 바로 이것이다.
 *
 * 영속 상태가 아닌 엔티티(new 로 생성했거나 트랜잭션 밖에서 조회된 경우)는
 * "분리 상태(Detached State)" 라고 하며, 이 경우에는 Dirty Checking 이 동작하지 않는다.
 * 분리 상태의 엔티티를 수정하고 반영하려면 repository.save() 를 명시적으로 호출해야 한다.
 *
 *
 * [엔티티에 비즈니스 로직(approve, reject, isPending)을 두는 이유]
 *
 * 상태를 직접 set 하는 방식(request.setStatus(APPROVED))을 쓰면
 * processedAt 설정을 빠뜨리거나, 이미 처리된 신청을 다시 처리하는 실수가 생길 수 있다.
 * approve(), reject() 메서드로 캡슐화하면 상태 변경에 필요한 모든 처리가
 * 항상 함께 실행되도록 강제할 수 있다.
 * 이를 "풍부한 도메인 모델(Rich Domain Model)" 패턴이라고 한다.
 */
