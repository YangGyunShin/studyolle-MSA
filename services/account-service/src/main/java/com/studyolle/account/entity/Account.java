package com.studyolle.account.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Getter
@Setter
@EqualsAndHashCode(of = "id")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Account {

    @Id
    @GeneratedValue
    private Long id;

    @Column(unique = true)
    private String email;

    @Column(unique = true)
    private String nickname;

    private String password;

    /**
     * 계정 권한 역할
     *
     * - 기본값: ROLE_USER (일반 사용자)
     * - ROLE_ADMIN 으로 변경 시 관리자 페이지(/admin/**) 접근 가능
     *
     * [주의] 이 값을 DB에서 변경해도 즉시 반영되지 않는다.
     * Spring Security는 로그인 시점에 이 값을 읽어 세션에 저장하고,
     * 이후 요청은 DB가 아닌 세션에서 권한을 꺼내 쓰기 때문이다.
     * -> 변경 후 반드시 재로그인 필요
     */
    @Column(nullable = false)
    @Builder.Default
    private String role = "ROLE_USER";

    private boolean emailVerified;

    private String emailCheckToken;

    private LocalDateTime joinedAt;

    private String bio;
    private String url;
    private String occupation;
    private String location;

    @Lob
    @Basic(fetch = FetchType.EAGER)
    private String profileImage;

    /*
     * =============================================
     * 관심 태그 목록
     * =============================================
     *
     * @ElementCollection
     * Account 엔티티 안에 String 값들의 컬렉션을 저장하는 방법이다.
     * 일반적인 @OneToMany 는 별도 엔티티 클래스가 필요하지만,
     * @ElementCollection 은 엔티티 없이 단순 값(String, Integer 등)을 컬렉션으로 저장할 수 있다.
     * 내부적으로 JPA 가 별도 테이블(account_tags)을 만들어서 관리한다.
     *
     * @CollectionTable(name = "account_tags", joinColumns = @JoinColumn(name = "account_id"))
     * 이 컬렉션이 저장될 테이블 이름과 외래 키를 지정한다.
     * joinColumns = @JoinColumn(name = "account_id") 는 account_tags 테이블에서 Account 를 참조하는 외래 키 컬럼 이름을 "account_id" 로 설정한다.
     * 결과적으로 account_tags 테이블은 (account_id, tag_title) 두 컬럼으로 구성된다.
     *
     * @Column(name = "tag_title")
     * 컬렉션 원소(String) 가 저장될 컬럼 이름을 "tag_title" 로 지정한다.
     * 이 설정이 없으면 JPA 가 기본 이름("tags")을 사용한다.
     *
     * fetch = FetchType.EAGER
     * Account 를 조회할 때 태그 목록도 함께(즉시) 로딩한다.
     * 설정 페이지에서 Account 를 가져오는 즉시 tags 를 사용하므로 EAGER 가 적합하다.
     * (LAZY 로 설정하면 트랜잭션 밖에서 tags 에 접근할 때 LazyInitializationException 이 발생한다.)
     *
     * @Builder.Default
     * Lombok @Builder 를 사용할 때 필드 기본값을 적용하기 위해 필요하다.
     * 이 애너테이션이 없으면 Account.builder().build() 로 생성 시 tags 가 null 이 된다.
     * @Builder.Default 가 있으면 new HashSet<>() 로 초기화된 상태로 생성된다.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "account_tags", joinColumns = @JoinColumn(name = "account_id"))
    @Column(name = "tag_title")
    @Builder.Default
    private Set<String> tags = new HashSet<>();

    /*
     * =============================================
     * 활동 지역 목록
     * =============================================
     *
     * tags 와 동일한 구조이며, 저장되는 값의 형식만 다르다.
     * tags  : 태그 이름 문자열 (예: "Java", "Spring Boot")
     * zones : 지역 표시 문자열 (예: "Seoul(서울)/서울특별시")
     *
     * [모노리틱과의 차이]
     * 모노리틱에서는 Tag, Zone 이라는 별도 엔티티가 있었고, Account 와 @ManyToMany 관계를 맺었다.
     * MSA 로 전환하면서 Tag/Zone 엔티티를 제거하고 문자열 컬렉션으로 단순화했다.
     * 메타데이터(전체 태그·지역 목록)는 metadata-service 또는 study-service 의 MetadataFeignClient 를 통해 별도 관리한다.
     *
     * account_zones 테이블 구조: (account_id, zone_name)
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "account_zones", joinColumns = @JoinColumn(name = "account_id"))
    @Column(name = "zone_name")
    @Builder.Default
    private Set<String> zones = new HashSet<>();

    // 알림 설정 - 기본값은 웹 알림만 on
    @Builder.Default private boolean studyCreatedByEmail = false;
    @Builder.Default private boolean studyCreatedByWeb = true;
    @Builder.Default private boolean studyEnrollmentResultByEmail = false;
    @Builder.Default private boolean studyEnrollmentResultByWeb = true;
    @Builder.Default private boolean studyUpdatedByEmail = false;
    @Builder.Default private boolean studyUpdatedByWeb = true;

    private LocalDateTime emailCheckTokenGeneratedAt;


    // [MSA 전환 메모]
    // 모노리틱의 @ManyToMany Tag/Zone 은 제거됨.
    // 태그/지역은 tagTitle/zoneName 문자열로 account_tags, account_zones 테이블에 저장.
    // (Phase 2 완료 후 별도 엔티티로 추가 예정)

    public void generateEmailCheckToken() {
        this.emailCheckToken = UUID.randomUUID().toString();
        this.emailCheckTokenGeneratedAt = LocalDateTime.now();
    }

    /**
     * [📌 설명] 계정 가입 완료 상태로 전환하는 도메인 메서드
     *
     * - 이 메서드는 사용자가 이메일 인증을 성공적으로 완료했을 때 호출된다.
     * - 이메일 인증은 사용자가 실제 이메일 주소를 소유하고 있는지 검증하는 절차이며,
     *   완료되면 계정의 상태를 '가입 완료'로 바꿔야 한다.
     *
     * [✔ 의미 있는 도메인 메서드로 설계된 이유]
     * - 단순히 필드에 값을 할당하는 것이지만, 그 자체로 중요한 도메인 이벤트(가입 완료)를 나타내므로,
     *   이를 `setEmailVerified(true)`와 같이 직접 호출하지 않고 메서드로 감싼다.
     * - 이는 도메인의 **의도를 명시적으로 표현**하는 객체지향 설계 방식이다.
     * - 외부에서는 반드시 이 메서드를 통해서만 가입 완료 처리를 하도록 강제할 수 있다.
     *
     * [💡 트랜잭션과의 관계]
     * - 이 메서드는 단순히 필드 값을 변경하는 것이므로 트랜잭션 내에서 호출되어야 DB에 반영된다.
     * - 일반적으로는 Service 계층에서 트랜잭션이 적용된 상태로 호출되며,
     *   그 안에서 accountRepository.save(account) 등을 통해 persist/merge 된다.
     *
     * - 회원가입 시에는 이메일 인증 여부가 false로 되어 있음
     * - 이 메서드는 인증이 성공적으로 완료되었을 때 호출되어 다음 두 가지 필드를 설정함:
     *
     *   1. emailVerified: true
     *   2. joinedAt: 현재 시각 (LocalDateTime.now())
     *
     * ✅ 이 두 값은 '사용자가 인증을 마쳤다'는 신호로 해석된다.
     *    → 시스템에서는 이 필드를 기준으로 인증된 사용자와 미인증 사용자를 구분할 수 있음
     *
     * ✅ 이 메서드는 Account 도메인의 순수한 비즈니스 로직이다.
     *    (의존성 없음, 외부 부작용 없음)
     */
    public void completeSignUp() {
        this.emailVerified = true;                // 이메일 인증 완료 상태로 전환
        this.joinedAt = LocalDateTime.now();     // 가입 시각을 현재 시간으로 설정
    }

    public boolean isValidToken(String token) {
        return this.emailCheckToken.equals(token);
    }

    /**
     * 이메일 인증 링크 재전송 가능 여부를 판단하는 메서드
     *
     * ✅ 주요 목적:
     *  - 사용자가 인증 이메일을 너무 자주 요청하는 것을 방지하여,
     *    시스템 자원 낭비 및 스팸 메일 전송을 최소화하기 위함
     *
     * ✅ 기본 동작:
     *  - 사용자의 마지막 인증 이메일 전송 시각(emailCheckTokenGeneratedAt)이
     *    현재 시간보다 1시간 이상 이전이라면 true를 반환하여,
     *    인증 이메일을 다시 보낼 수 있도록 허용함
     *
     * ✅ 사용 위치:
     *  - `/resend-confirm-email` 요청 처리 시, 인증 이메일 전송을 제한하는 로직에서 호출됨
     *
     * ✅ 반환값 의미:
     *  - true  → 이메일 재전송 허용
     *  - false → 1시간이 지나지 않았으므로 재전송 금지
     *
     * ✅ 예외 사항:
     *  - emailCheckTokenGeneratedAt이 null인 경우 NPE 발생 가능
     *    → 해당 필드가 항상 초기화된다는 전제 하에 사용되고 있음
     *    → 안전하게 처리하려면 null 체크 로직을 추가할 수도 있음
     */
    public boolean canSendConfirmEmail() {
        // 📌 현재 시간에서 1시간을 뺀 시점 이후에 토큰이 생성되었으면 아직 재전송 불가
        // → 즉, 마지막 생성 시점이 1시간 전보다 더 최근이면 false
        return this.emailCheckTokenGeneratedAt.isBefore(LocalDateTime.now().minusHours(1));
    }
}