package com.studyolle.modules.study.entity;

import com.studyolle.modules.account.entity.Account;
import com.studyolle.modules.account.security.UserAccount;
import com.studyolle.modules.tag.Tag;
import com.studyolle.modules.zone.Zone;
import jakarta.persistence.*;
import lombok.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Study 엔티티 - 스터디 그룹의 모든 상태와 관계를 표현하는 도메인 모델
 *
 * =============================================
 * 핵심 책임
 * =============================================
 *
 * - 스터디 생성 및 기본 정보 관리 (path, title, description, image)
 * - 상태 전이 관리 (공개/종료/모집 시작/중단)
 * - 구성원 관리 (관리자/멤버 추가/제거)
 * - 비즈니스 규칙 검증 (가입 가능 여부, 삭제 가능 여부, 모집 상태 변경 가능 여부)
 *
 * =============================================
 * @NamedEntityGraph - N+1 쿼리 최적화 전략
 * =============================================
 *
 * 이 엔티티는 여러 개의 @ManyToMany 연관관계를 가지고 있어,
 * 기본 지연 로딩(LAZY) 전략에서는 N+1 쿼리 문제가 발생할 수 있습니다.
 *
 * 이를 방지하기 위해 @NamedEntityGraph로 "어떤 연관관계를 함께 로딩할지"를
 * 명시적으로 선언하고, Repository 메서드에서 필요한 그래프를 선택하여 사용합니다.
 *
 * 각 EntityGraph는 사용 시나리오에 맞게 최소한의 연관관계만 로딩하도록 설계되었습니다:
 *
 * - Study.withAll: 스터디 상세 페이지 (모든 연관 엔티티 필요)
 * - Study.withTagsAndManagers: 태그 설정 화면 (태그 + 관리자 권한 확인)
 * - Study.withZonesAndManagers: 지역 설정 화면 (지역 + 관리자 권한 확인)
 * - Study.withManagers: 상태 변경 화면 (관리자 권한 확인만 필요)
 * - Study.withTagsAndZones: 스터디 검색/추천 (태그 + 지역 정보만 필요)
 *
 * =============================================
 * 상태 전이 규칙 (State Machine)
 * =============================================
 *
 * 스터디의 상태 전이는 다음과 같은 규칙을 따릅니다:
 *
 * [생성] -> published=false, closed=false, recruiting=false
 *   |
 *   v
 * [공개(publish)] -> published=true (되돌릴 수 없음)
 *   |
 *   +-- [모집 시작(startRecruit)] -> recruiting=true (1시간 쿨다운)
 *   |    |
 *   |    +-- [모집 중단(stopRecruit)] -> recruiting=false (1시간 쿨다운)
 *   |
 *   v
 * [종료(close)] -> closed=true (되돌릴 수 없음)
 *
 * - 공개 전에만 삭제(remove) 가능
 * - 공개 후에만 종료 가능
 * - 모집 상태 변경은 1시간 간격으로만 가능
 *
 * =============================================
 * @EqualsAndHashCode(of = "id") 사용 이유
 * =============================================
 *
 * Set 컬렉션(@ManyToMany)에서 Study 객체를 올바르게 비교하기 위해
 * equals/hashCode를 id 기반으로 재정의합니다.
 * 연관 엔티티의 순환 참조로 인한 무한 루프를 방지하는 효과도 있습니다.
 */
@NamedEntityGraph(name = "Study.withAll", attributeNodes = {
        @NamedAttributeNode("tags"),
        @NamedAttributeNode("zones"),
        @NamedAttributeNode("managers"),
        @NamedAttributeNode("members")
})
@NamedEntityGraph(name = "Study.withTagsAndManagers", attributeNodes = {
        @NamedAttributeNode("tags"),
        @NamedAttributeNode("managers")
})
@NamedEntityGraph(name = "Study.withZonesAndManagers", attributeNodes = {
        @NamedAttributeNode("zones"),
        @NamedAttributeNode("managers")
})
@NamedEntityGraph(name = "Study.withManagers", attributeNodes = {
        @NamedAttributeNode("managers")
})
@NamedEntityGraph(name = "Study.withTagsAndZones", attributeNodes = {
        @NamedAttributeNode("tags"),
        @NamedAttributeNode("zones")
})
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Study {

    // ============================
    // 식별자 및 연관 엔티티
    // ============================

    @Id
    @GeneratedValue
    private Long id;

    /**
     * 스터디 관리자(운영자) 목록.
     * 스터디 생성자는 자동으로 관리자로 등록되며,
     * 설정 수정, 상태 변경, 삭제 등의 권한을 가집니다.
     */
    @ManyToMany
    private Set<Account> managers = new HashSet<>();

    /**
     * 일반 참여자(멤버) 목록.
     * 모집 중인 공개 스터디에 가입한 사용자들입니다.
     */
    @ManyToMany
    private Set<Account> members = new HashSet<>();

    /**
     * 스터디 주제 태그 목록.
     * Tag는 사용자가 자유롭게 생성할 수 있는 "개방형 데이터"입니다.
     * 사용자 관심사와 매칭하여 스터디 추천에 활용됩니다.
     */
    @ManyToMany
    private Set<Tag> tags = new HashSet<>();

    /**
     * 스터디 활동 지역 목록.
     * Zone은 CSV에서 사전 로드된 "폐쇄형 데이터"로, 새로 생성하지 않고 선택만 합니다.
     * 사용자 활동 지역과 매칭하여 스터디 추천에 활용됩니다.
     */
    @ManyToMany
    private Set<Zone> zones = new HashSet<>();

    // ============================
    // 스터디 기본 정보
    // ============================

    /**
     * URL에서 사용되는 고유 식별자.
     * 예: /study/springboot-study
     * 한글, 영문 소문자, 숫자, 하이픈, 언더스코어로 2~20자 구성됩니다.
     */
    @Column(unique = true)
    private String path;

    /** 사용자에게 표시되는 스터디 이름 (최대 50자) */
    private String title;

    /** 간략한 소개 (카드형 목록에서 표시, 최대 100자) */
    private String shortDescription;

    /**
     * 상세 소개 (에디터로 작성된 HTML 포함 가능).
     * @Lob: CLOB/TEXT 타입으로 매핑 (대용량 텍스트)
     * @Basic(fetch = FetchType.EAGER): 스터디 조회 시 항상 함께 로딩
     */
    @Lob
    @Basic(fetch = FetchType.EAGER)
    private String fullDescription;

    /**
     * 배너 이미지 (Base64 인코딩 데이터).
     * @Lob으로 선언하여 대용량 데이터 저장이 가능합니다.
     */
    @Lob
    @Basic(fetch = FetchType.EAGER)
    private String image;

    // ============================
    // 상태 관리 필드
    // ============================

    private LocalDateTime publishedDateTime;            // 공개 시점
    private LocalDateTime closedDateTime;               // 종료 시점
    private LocalDateTime recruitingUpdatedDateTime;    // 모집 상태 마지막 변경 시각

    private boolean recruiting;     // 현재 팀원 모집 중 여부
    private boolean published;      // 스터디 공개 여부
    private boolean closed;         // 스터디 종료 여부
    private boolean useBanner;      // 배너 이미지 표시 여부

    /**
     * 스터디 가입 방식.
     * - OPEN: 자유 가입 (즉시 멤버 등록, JoinRequest 생성 안 함)
     * - APPROVAL_REQUIRED: 승인제 (JoinRequest 생성 -> 관리자 승인 -> 멤버 등록)
     *
     * @Builder.Default로 기본값을 OPEN으로 설정하여
     * 기존 스터디들이 영향을 받지 않도록 합니다.
     *
     * [사용처]
     * - StudyController.joinStudy(): 가입 방식에 따라 즉시가입 / 신청 분기
     * - StudyController.viewStudy(): 뷰에서 버튼 분기를 위한 모델 데이터 준비
     * - StudySettingsController.updateJoinType(): 관리자가 가입 방식 변경
     * - 뷰(study-info fragment): isApprovalRequired()로 버튼/뱃지 표시 분기
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private JoinType joinType = JoinType.OPEN;

    private int memberCount;        // 현재 멤버 수 (비정규화 필드, 조회 성능 최적화용)

    // ============================
    // 관리자 관련 비즈니스 로직
    // ============================

    /** 관리자를 추가합니다. 주로 스터디 생성 시 생성자를 관리자로 등록할 때 사용됩니다. */
    public void addManager(Account account) {
        this.managers.add(account);
        this.memberCount++;
    }

    /** 특정 사용자가 이 스터디의 관리자인지 확인합니다. */
    public boolean isManagerOf(Account account) {
        return this.getManagers().contains(account);
    }

    /** 뷰에서 사용하는 관리자 여부 확인 (UserAccount 기반). */
    public boolean isManager(UserAccount userAccount) {
        return this.managers.contains(userAccount.getAccount());
    }

    // ============================
    // 멤버 관련 비즈니스 로직
    // ============================

    /**
     * 멤버를 추가하고 memberCount를 증가시킵니다.
     * memberCount는 성능 최적화를 위한 비정규화 필드로,
     * members.size()를 매번 계산하지 않아도 되게 합니다.
     */
    public void addMember(Account account) {
        this.getMembers().add(account);
        this.memberCount++;
    }

    /** 멤버를 제거하고 memberCount를 감소시킵니다. */
    public void removeMember(Account account) {
        this.getMembers().remove(account);
        this.memberCount--;
    }

    /** 뷰에서 사용하는 멤버 여부 확인 (UserAccount 기반). */
    public boolean isMember(UserAccount userAccount) {
        return this.members.contains(userAccount.getAccount());
    }

    // ============================
    // 가입 가능 여부 판단
    // ============================

    /**
     * 현재 사용자가 이 스터디에 가입할 수 있는지 판단합니다.
     *
     * 가입 조건:
     * 1. 스터디가 공개(published) 상태
     * 2. 현재 모집 중(recruiting)
     * 3. 이미 멤버가 아님
     * 4. 관리자가 아님
     *
     * 뷰(Thymeleaf)에서 "가입" 버튼의 표시 여부를 결정하는 데 사용됩니다.
     */
    public boolean isJoinable(UserAccount userAccount) {
        Account account = userAccount.getAccount();
        return this.isPublished() && this.isRecruiting()
                && !this.members.contains(account)
                && !this.managers.contains(account);
    }

    /**
     * 이 스터디가 승인제인지 확인합니다.
     *
     * 뷰(Thymeleaf)에서 ${study.approvalRequired}로 접근하여
     * 가입 버튼과 뱃지를 분기하는 데 사용됩니다.
     * 기존 isJoinable(), isRemovable()과 같은 패턴입니다.
     */
    public boolean isApprovalRequired() {
        return this.joinType == JoinType.APPROVAL_REQUIRED;
    }

    // ============================
    // 상태 전이 로직
    // ============================

    /**
     * 모집 상태 변경 가능 여부를 판단합니다.
     *
     * 조건:
     * 1. 스터디가 공개 상태여야 함
     * 2. 마지막 모집 상태 변경으로부터 1시간 이상 경과해야 함
     *    (서버 부하 방지 및 과도한 상태 변경 제한)
     */
    public boolean canUpdateRecruiting() {
        return this.published && (
                this.recruitingUpdatedDateTime == null ||
                        this.recruitingUpdatedDateTime.isBefore(LocalDateTime.now().minusHours(1))
        );
    }

    /**
     * 스터디를 공개 처리합니다.
     *
     * 전제 조건: 종료되지 않고 아직 공개되지 않은 상태
     * 공개는 비가역적 작업입니다 (되돌릴 수 없음).
     *
     * @throws RuntimeException 이미 공개되었거나 종료된 상태에서 호출한 경우
     */
    public void publish() {
        if (!this.closed && !this.published) {
            this.published = true;
            this.publishedDateTime = LocalDateTime.now();
        } else {
            throw new RuntimeException("스터디를 공개할 수 없는 상태입니다. 스터디를 이미 공개했거나 종료했습니다.");
        }
    }

    /**
     * 스터디를 종료 처리합니다.
     *
     * 전제 조건: 공개된 상태이며 아직 종료되지 않은 상태
     * 종료는 비가역적 작업입니다 (되돌릴 수 없음).
     *
     * @throws RuntimeException 공개되지 않았거나 이미 종료된 상태에서 호출한 경우
     */
    public void close() {
        if (this.published && !this.closed) {
            this.closed = true;
            this.closedDateTime = LocalDateTime.now();
        } else {
            throw new RuntimeException("스터디를 종료할 수 없습니다. 스터디를 공개하지 않았거나 이미 종료한 스터디입니다.");
        }
    }

    /**
     * 팀원 모집을 시작합니다.
     *
     * @throws RuntimeException 쿨다운 기간 내이거나 공개되지 않은 상태에서 호출한 경우
     */
    public void startRecruit() {
        if (canUpdateRecruiting()) {
            this.recruiting = true;
            this.recruitingUpdatedDateTime = LocalDateTime.now();
        } else {
            throw new RuntimeException("인원 모집을 시작할 수 없습니다. 스터디를 공개하거나 한 시간 뒤 다시 시도하세요.");
        }
    }

    /**
     * 팀원 모집을 중단합니다.
     *
     * @throws RuntimeException 쿨다운 기간 내이거나 공개되지 않은 상태에서 호출한 경우
     */
    public void stopRecruit() {
        if (canUpdateRecruiting()) {
            this.recruiting = false;
            this.recruitingUpdatedDateTime = LocalDateTime.now();
        } else {
            throw new RuntimeException("인원 모집을 멈출 수 없습니다. 스터디를 공개하거나 한 시간 뒤 다시 시도하세요.");
        }
    }

    // ============================
    // 유틸리티 메서드
    // ============================

    /**
     * 스터디 삭제 가능 여부를 판단합니다.
     * 공개(publish) 전 상태에서만 삭제가 허용됩니다.
     * 공개 후에는 참여자가 존재할 수 있으므로 삭제가 불가능합니다.
     */
    public boolean isRemovable() {
        return !this.published;
    }

    /**
     * URL에 포함될 path를 UTF-8로 안전하게 인코딩합니다.
     * 한글 path를 URL에서 사용할 수 있도록 변환합니다.
     * 예: "스프링부트-스터디" -> "%EC%8A%A4%ED%94%84%EB%A7%81..."
     */
    public String getEncodedPath() {
        return URLEncoder.encode(this.path, StandardCharsets.UTF_8);
    }
}