package com.studyolle.modules.account.entity;

import com.studyolle.modules.tag.Tag;
import com.studyolle.modules.zone.Zone;
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

    private boolean studyCreatedByEmail;
    private boolean studyCreatedByWeb = true;
    private boolean studyEnrollmentResultByEmail;
    private boolean studyEnrollmentResultByWeb = true;
    private boolean studyUpdatedByEmail;
    private boolean studyUpdatedByWeb = true;

    @ManyToMany
    private Set<Tag> tags = new HashSet<>();


    // [주의사항]
    // - 연관관계 조인을 위해 account_zones라는 중간 테이블이 자동 생성됨
    // - @ManyToMany는 LAZY가 기본이며, toString, equals 등에 연관 엔티티가 직접 접근되면 LazyInitializationException 유발 가능
    // - 필요 시 @JoinTable을 명시해 중간 테이블 이름과 컬럼명을 커스터마이징 가능
    @ManyToMany
    private Set<Zone> zones = new HashSet<>();

    private LocalDateTime emailCheckTokenGeneratedAt;

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
     * - 이 메서드는 인증이 성공적으로 완료되었을 때 호출되어
     *   다음 두 가지 필드를 설정함:
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