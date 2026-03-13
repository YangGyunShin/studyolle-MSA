package com.studyolle.study.entity;

import jakarta.persistence.*;
import lombok.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Study 엔티티
 *
 * =============================================
 * [모노리틱과의 핵심 차이 — 길 1]
 * =============================================
 *
 * 모노리틱에서는 Account, Tag, Zone 엔티티를 @ManyToMany 로 직접 참조했으나,
 * MSA 에서는 각 서비스가 독립적인 DB 를 갖기 때문에 직접 참조가 불가능하다.
 *
 * [길 1 결정 이유]
 * Tag 와 Zone 은 metadata-service 가 소유(진실의 원천).
 * study-service 는 ID 만 보관해 metadata-service 에 대한 DB 의존성을 완전히 제거한다.
 * 이렇게 하면 캐시 동기화 문제(길 2 의 단점)도 없고,
 * 나중에 RabbitMQ/Kafka 를 붙일 때도 복잡성이 증가하지 않는다.
 *
 *   모노리틱 필드                            MSA 대체 필드
 *   @ManyToMany Set<Account> managers  →  @ElementCollection Set<Long> managerIds
 *   @ManyToMany Set<Account> members   →  @ElementCollection Set<Long> memberIds
 *   @ManyToMany Set<Tag>     tags      →  @ElementCollection Set<Long> tagIds
 *   @ManyToMany Set<Zone>    zones     →  @ElementCollection Set<Long> zoneIds
 *
 * =============================================
 * 상태 전이 규칙 (State Machine) — 모노리틱과 동일
 * =============================================
 *
 * [생성] → published=false, closed=false, recruiting=false
 *   |
 *   v
 * [공개(publish)] → published=true (비가역)
 *   |
 *   +-- [모집 시작(startRecruit)] → recruiting=true (1시간 쿨다운)
 *   |    |
 *   |    +-- [모집 중단(stopRecruit)] → recruiting=false (1시간 쿨다운)
 *   |
 *   v
 * [종료(close)] → closed=true (비가역)
 *
 * - 공개 전에만 삭제(remove) 가능
 * - 공개 후에만 종료 가능
 * - 모집 상태 변경은 1시간 간격으로만 가능
 *
 * =============================================
 * @EqualsAndHashCode(of = "id") — 모노리틱과 동일한 이유
 * =============================================
 *
 * Set 컬렉션에서 Study 객체를 올바르게 비교하기 위해 id 기반으로 재정의.
 * 연관 엔티티의 순환 참조로 인한 무한 루프도 방지한다.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Study {

    @Id
    @GeneratedValue
    private Long id;

    // ============================
    // [길 1] ID 기반 관계 — @ElementCollection
    // ============================

    /**
     * 스터디 관리자(운영자) ID 목록.
     *
     * [모노리틱] @ManyToMany Set<Account> managers
     * [MSA 길 1] @ElementCollection Set<Long> managerIds
     *
     * account-service 의 Account.id 를 참조하지만,
     * FK 제약 없이 ID 값만 저장한다. (서비스 간 DB 분리 원칙)
     * 컬렉션 테이블명: study_manager_ids
     */
    @ElementCollection
    @CollectionTable(name = "study_manager_ids", joinColumns = @JoinColumn(name = "study_id"))
    @Column(name = "account_id")
    @Builder.Default
    private Set<Long> managerIds = new HashSet<>();

    /**
     * 일반 참여자(멤버) ID 목록.
     * 컬렉션 테이블명: study_member_ids
     */
    @ElementCollection
    @CollectionTable(name = "study_member_ids", joinColumns = @JoinColumn(name = "study_id"))
    @Column(name = "account_id")
    @Builder.Default
    private Set<Long> memberIds = new HashSet<>();

    /**
     * 스터디 주제 태그 ID 목록.
     *
     * [모노리틱] @ManyToMany Set<Tag> tags
     * [MSA 길 1] metadata-service 의 Tag.id 만 저장
     *
     * 검색 시: MetadataFeignClient.findTagIdsByKeyword() 결과와 대조
     * 추천 시: account-service 가 반환한 interestTagIds 와 대조
     * 컬렉션 테이블명: study_tag_ids
     */
    @ElementCollection
    @CollectionTable(name = "study_tag_ids", joinColumns = @JoinColumn(name = "study_id"))
    @Column(name = "tag_id")
    @Builder.Default
    private Set<Long> tagIds = new HashSet<>();

    /**
     * 스터디 활동 지역 ID 목록.
     *
     * [모노리틱] @ManyToMany Set<Zone> zones
     * [MSA 길 1] metadata-service 의 Zone.id 만 저장
     * 컬렉션 테이블명: study_zone_ids
     */
    @ElementCollection
    @CollectionTable(name = "study_zone_ids", joinColumns = @JoinColumn(name = "study_id"))
    @Column(name = "zone_id")
    @Builder.Default
    private Set<Long> zoneIds = new HashSet<>();

    // ============================
    // 스터디 기본 정보 — 모노리틱과 동일
    // ============================

    /**
     * URL 에서 사용되는 고유 식별자.
     * 예: /study/springboot-study
     * 한글, 영문 소문자, 숫자, 하이픈, 언더스코어로 2~20자 구성.
     */
    @Column(unique = true)
    private String path;

    /** 사용자에게 표시되는 스터디 이름 (최대 50자) */
    private String title;

    /** 간략한 소개 (카드형 목록에서 표시, 최대 100자) */
    private String shortDescription;

    /**
     * 상세 소개 (에디터로 작성된 HTML 포함 가능).
     *
     * @Lob: CLOB/TEXT 타입으로 매핑 (대용량 텍스트)
     * @Basic(fetch = EAGER): 스터디 조회 시 항상 함께 로딩
     */
    @Lob
    @Basic(fetch = FetchType.EAGER)
    private String fullDescription;

    /** 배너 이미지 (Base64 인코딩 데이터) */
    @Lob
    @Basic(fetch = FetchType.EAGER)
    private String image;

    // ============================
    // 상태 관리 필드 — 모노리틱과 동일
    // ============================

    private LocalDateTime publishedDateTime;
    private LocalDateTime closedDateTime;
    private LocalDateTime recruitingUpdatedDateTime;

    private boolean recruiting;
    private boolean published;
    private boolean closed;
    private boolean useBanner;

    /**
     * 스터디 가입 방식.
     * @Builder.Default 로 기본값을 OPEN 으로 설정해
     * 기존 스터디들이 영향을 받지 않도록 한다.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private JoinType joinType = JoinType.OPEN;

    /**
     * 현재 멤버 수 (비정규화 필드, 조회 성능 최적화용).
     * members.size() 를 매번 계산하지 않아도 되게 한다.
     */
    private int memberCount;

    // ============================
    // 관리자 관련 비즈니스 로직
    // ============================

    /**
     * 관리자를 추가한다.
     *
     * [모노리틱 변경]
     * addManager(Account account) → addManager(Long accountId)
     * Account 객체 대신 ID 만 받아 managerIds Set 에 추가한다.
     */
    public void addManager(Long accountId) {
        this.managerIds.add(accountId);
        this.memberCount++;
    }

    /** 특정 사용자가 이 스터디의 관리자인지 확인한다. */
    public boolean isManagerOf(Long accountId) {
        return this.managerIds.contains(accountId);
    }

    // ============================
    // 멤버 관련 비즈니스 로직
    // ============================

    /**
     * 멤버를 추가하고 memberCount 를 증가시킨다.
     *
     * [모노리틱 변경]
     * addMember(Account account) → addMember(Long accountId)
     */
    public void addMember(Long accountId) {
        this.memberIds.add(accountId);
        this.memberCount++;
    }

    /**
     * 멤버를 제거하고 memberCount 를 감소시킨다.
     *
     * [모노리틱 변경]
     * removeMember(Account account) → removeMember(Long accountId)
     */
    public void removeMember(Long accountId) {
        this.memberIds.remove(accountId);
        this.memberCount--;
    }

    // ============================
    // 가입 가능 여부 판단
    // ============================

    /**
     * 현재 사용자가 이 스터디에 가입할 수 있는지 판단한다.
     *
     * 가입 조건 (모노리틱과 동일한 로직, Account → Long 으로 파라미터만 변경):
     * 1. 스터디가 공개(published) 상태
     * 2. 현재 모집 중(recruiting)
     * 3. 이미 멤버가 아님
     * 4. 관리자가 아님
     *
     * [모노리틱 변경]
     * isJoinable(UserAccount userAccount) → isJoinable(Long accountId)
     * UserAccount/Account 의존성 제거
     */
    public boolean isJoinable(Long accountId) {
        return this.isPublished() && this.isRecruiting()
                && !this.memberIds.contains(accountId)
                && !this.managerIds.contains(accountId);
    }

    /** 이 스터디가 승인제인지 확인한다. */
    public boolean isApprovalRequired() {
        return this.joinType == JoinType.APPROVAL_REQUIRED;
    }

    // ============================
    // 상태 전이 로직 — 모노리틱과 완전히 동일
    // ============================

    /**
     * 모집 상태 변경 가능 여부를 판단한다.
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
     * 스터디를 공개 처리한다.
     * 공개는 비가역적 작업이다 (되돌릴 수 없음).
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
     * 스터디를 종료 처리한다.
     * 종료는 비가역적 작업이다 (되돌릴 수 없음).
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
     * 팀원 모집을 시작한다.
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
     * 팀원 모집을 중단한다.
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
    // 유틸리티 메서드 — 모노리틱과 동일
    // ============================

    /**
     * 스터디 삭제 가능 여부를 판단한다.
     * 공개(publish) 전 상태에서만 삭제가 허용된다.
     */
    public boolean isRemovable() {
        return !this.published;
    }

    /**
     * URL 에 포함될 path 를 UTF-8 로 안전하게 인코딩한다.
     * 한글 path 를 URL 에서 사용할 수 있도록 변환한다.
     */
    public String getEncodedPath() {
        return URLEncoder.encode(this.path, StandardCharsets.UTF_8);
    }
}