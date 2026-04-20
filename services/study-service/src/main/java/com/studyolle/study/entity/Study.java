package com.studyolle.study.entity;

import jakarta.persistence.*;
import lombok.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

// 스터디 엔티티. study-service 의 핵심 도메인 객체다.
//
// @EqualsAndHashCode(of = "id"):
// Set<Study> 처럼 컬렉션에서 동등성 비교를 할 때 id 만 사용한다.
// 기본 equals/hashCode 는 객체 참조(메모리 주소)를 비교하므로
// DB 에서 조회한 같은 스터디라도 서로 다른 객체이면 다르다고 판단해버린다.
// id 기반으로 재정의하면 같은 id 를 가진 객체는 항상 동일하게 취급된다.
// 또한 엔티티가 다른 엔티티를 참조하는 경우, 기본 hashCode 는 참조된 엔티티의
// 모든 필드까지 순환하면서 계산하려다가 무한 루프에 빠질 수 있다.
// id 만 사용하면 이 문제도 차단된다.
//
// @Builder + @NoArgsConstructor + @AllArgsConstructor 를 함께 쓰는 이유:
// @Builder 는 모든 필드를 받는 생성자를 필요로 한다(@AllArgsConstructor).
// JPA 는 프록시 객체 생성을 위해 기본 생성자를 필요로 한다(@NoArgsConstructor).
// 두 어노테이션을 함께 선언해야 빌더와 JPA 가 모두 정상 동작한다.
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

    // =====================================================================
    // @ElementCollection 으로 관리하는 ID 컬렉션
    //
    // account-service 의 Account, metadata-service 의 Tag/Zone 은
    // 이 서비스와 물리적으로 다른 DB 에 있다.
    // 따라서 @ManyToMany 로 엔티티를 직접 참조할 수 없고,
    // DB 레벨의 FK 를 걸 수도 없다.
    // 해결책: 관련 엔티티의 ID(Long) 값만 별도 컬렉션 테이블에 저장한다.
    //
    // @ElementCollection: 엔티티가 아닌 단순 값(Long)의 컬렉션을 별도 테이블에 저장한다.
    // @CollectionTable: 컬렉션이 저장될 테이블 이름과 FK 컬럼을 지정한다.
    // @Column: 컬렉션 테이블에서 값이 저장될 컬럼 이름을 지정한다.
    // @Builder.Default: @Builder 사용 시 초기화 표현식(new HashSet<>())이 무시되는
    //   문제를 방지한다. 이 어노테이션이 없으면 빌더로 생성한 객체에서 컬렉션이 null 이 된다.
    // =====================================================================

    // 스터디 관리자(운영자) ID 목록. → 컬렉션 테이블: study_manager_ids
    // account-service 의 Account.id 값을 저장한다.
    @ElementCollection
    @CollectionTable(name = "study_manager_ids", joinColumns = @JoinColumn(name = "study_id"))
    @Column(name = "account_id")
    @Builder.Default
    private Set<Long> managerIds = new HashSet<>();

    // 스터디 일반 멤버 ID 목록. → 컬렉션 테이블: study_member_ids
    @ElementCollection
    @CollectionTable(name = "study_member_ids", joinColumns = @JoinColumn(name = "study_id"))
    @Column(name = "account_id")
    @Builder.Default
    private Set<Long> memberIds = new HashSet<>();

    // 스터디 주제 태그 ID 목록. → 컬렉션 테이블: study_tag_ids
    // metadata-service 의 Tag.id 값을 저장한다.
    // 검색 시 MetadataFeignClient 에서 키워드로 tagId 목록을 먼저 조회한 뒤
    // QueryDSL 에서 study.tagIds.any().in(tagIds) 로 매칭한다.
    @ElementCollection
    @CollectionTable(name = "study_tag_ids", joinColumns = @JoinColumn(name = "study_id"))
    @Column(name = "tag_id")
    @Builder.Default
    private Set<Long> tagIds = new HashSet<>();

    // 스터디 활동 지역 ID 목록. → 컬렉션 테이블: study_zone_ids
    // metadata-service 의 Zone.id 값을 저장한다.
    @ElementCollection
    @CollectionTable(name = "study_zone_ids", joinColumns = @JoinColumn(name = "study_id"))
    @Column(name = "zone_id")
    @Builder.Default
    private Set<Long> zoneIds = new HashSet<>();

    // =====================================================================
    // 스터디 기본 정보
    // =====================================================================

    // URL 에서 스터디를 식별하는 고유 경로. 예: /study/spring-boot-study
    // 한글, 영문 소문자, 숫자, 하이픈, 언더스코어로 2~20자 구성.
    // unique = true: DB 레벨에서 중복을 막는다(UNIQUE 제약).
    @Column(unique = true)
    private String path;

    // 사용자에게 보이는 스터디 이름. 최대 50자
    private String title;

    // 카드 목록에 표시되는 한 줄 소개. 최대 100자
    private String shortDescription;

    // 상세 페이지에 표시되는 본문. 에디터로 작성한 HTML 이 포함될 수 있어 크기가 크다.
    // @Lob: VARCHAR 로는 저장할 수 없는 대용량 텍스트를 CLOB/TEXT 타입으로 매핑한다.
    // @Basic(fetch = EAGER): 스터디를 조회할 때 이 필드도 함께 로딩한다.
    //   @Lob 은 JPA 구현체에 따라 LAZY 로 동작할 수 있어 명시적으로 EAGER 를 지정한다.
    @Lob
    @Basic(fetch = FetchType.EAGER)
    private String fullDescription;

    // 배너 이미지. Base64 인코딩된 이미지 데이터를 문자열로 저장한다.
    @Lob
    @Basic(fetch = FetchType.EAGER)
    private String image;

    // =====================================================================
    // 상태 관리 필드 (State Machine)
    //
    // 상태 전이 규칙:
    //   [생성]  → published=false, closed=false, recruiting=false
    //     ↓
    //   [공개]  → published=true  (비가역 — 한번 공개하면 되돌릴 수 없음)
    //     ↓
    //   [모집]  → recruiting=true / false  (1시간 쿨다운 적용)
    //     ↓
    //   [종료]  → closed=true  (비가역 — 한번 종료하면 되돌릴 수 없음)
    //
    // 삭제(remove)는 published=false 인 상태에서만 가능하다.
    // =====================================================================

    private LocalDateTime publishedDateTime;    // 공개 처리된 시각
    private LocalDateTime closedDateTime;       // 종료 처리된 시각
    private LocalDateTime recruitingUpdatedDateTime; // 마지막 모집 상태 변경 시각 (쿨다운 계산용)

    private boolean recruiting;  // 현재 팀원 모집 중인지
    private boolean published;   // 공개된 스터디인지
    private boolean closed;      // 종료된 스터디인지
    private boolean useBanner;   // 배너 이미지 사용 여부

    // 가입 방식. @Builder.Default 로 기본값을 OPEN 으로 설정한다.
    // nullable = false: 이 값 없이는 저장할 수 없다.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private JoinType joinType = JoinType.OPEN;

    // 현재 멤버 수. memberIds.size() 를 매번 계산하는 대신 이 값을 사용한다.
    // addMember() / removeMember() / addManager() 호출 시 항상 함께 갱신된다.
    // 이처럼 계산할 수 있는 값을 미리 저장해두는 것을 "비정규화" 라고 한다.
    // 목록 조회에서 memberIds 컬렉션 테이블까지 JOIN 하지 않아도 되므로 성능이 좋아진다.
    private int memberCount;

    // =====================================================================
    // 관리자 관련 메서드
    // =====================================================================

    // 관리자를 추가하고 memberCount 를 증가시킨다.
    // 스터디 생성 시 생성자가 관리자로 등록될 때 StudyService 에서 호출한다.
    public void addManager(Long accountId) {
        this.managerIds.add(accountId);
        this.memberCount++;
    }

    // 특정 사용자가 이 스터디의 관리자인지 확인한다.
    // Set.contains() 는 O(1) 연산이므로 빠르다.
    public boolean isManagerOf(Long accountId) {
        return this.managerIds.contains(accountId);
    }

    // =====================================================================
    // 멤버 관련 메서드
    // =====================================================================

    // 멤버를 추가하고 memberCount 를 증가시킨다.
    // OPEN 가입 시 StudyService.joinStudy() 에서 바로 호출되고,
    // 승인제 가입 시 StudyService.approveJoinRequest() 에서 호출된다.
    public void addMember(Long accountId) {
        this.memberIds.add(accountId);
        this.memberCount++;
    }

    // 멤버를 제거하고 memberCount 를 감소시킨다.
    // 스터디 탈퇴 시 StudyService.leaveStudy() 에서 호출된다.
    public void removeMember(Long accountId) {
        this.memberIds.remove(accountId);
        this.memberCount--;
    }

    // =====================================================================
    // 가입 가능 여부 판단
    // =====================================================================

    // 특정 사용자가 이 스터디에 가입할 수 있는지 판단한다.
    // 4가지 조건이 모두 충족되어야 가입 가능하다:
    //   1. 스터디가 공개(published) 상태
    //   2. 현재 팀원 모집 중(recruiting)
    //   3. 이미 멤버가 아님
    //   4. 관리자가 아님 (관리자는 이미 스터디 참여자)
    public boolean isJoinable(Long accountId) {
        return this.isPublished() && this.isRecruiting()
                && !this.memberIds.contains(accountId)
                && !this.managerIds.contains(accountId);
    }

    // 이 스터디가 승인제 가입인지 확인한다.
    // StudyController.joinStudy() 에서 즉시가입 / 신청접수 분기에 사용한다.
    public boolean isApprovalRequired() {
        return this.joinType == JoinType.APPROVAL_REQUIRED;
    }

    // =====================================================================
    // 상태 전이 메서드
    // =====================================================================

    // 모집 상태를 변경할 수 있는 조건을 검사한다.
    // 조건 1: 스터디가 공개 상태여야 한다.
    // 조건 2: 최초 변경이거나(recruitingUpdatedDateTime == null),
    //         마지막 변경으로부터 1시간 이상 지났어야 한다.
    // 쿨다운을 두는 이유: 관리자가 모집 상태를 반복해서 바꿔 알림을 도배하는 것을 막는다.
    public boolean canUpdateRecruiting() {
        return this.published && (
                this.recruitingUpdatedDateTime == null ||
                        this.recruitingUpdatedDateTime.isBefore(LocalDateTime.now().minusHours(1))
        );
    }

    // 스터디를 공개한다. 한번 공개하면 되돌릴 수 없다(비가역).
    // 공개 전이고 종료되지 않은 상태에서만 호출 가능하다.
    // 조건이 맞지 않으면 RuntimeException 을 던진다.
    // 예외를 던지는 이유: 이 메서드가 호출되면 안 되는 상황이므로
    // 조용히 무시(return)하는 것보다 예외로 명확히 알리는 것이 적절하다.
    public void publish() {
        if (!this.closed && !this.published) {
            this.published = true;
            this.publishedDateTime = LocalDateTime.now();
        } else {
            throw new RuntimeException("스터디를 공개할 수 없는 상태입니다. 스터디를 이미 공개했거나 종료했습니다.");
        }
    }

    // 스터디를 종료한다. 한번 종료하면 되돌릴 수 없다(비가역).
    // 공개된 상태이고 아직 종료되지 않았을 때만 호출 가능하다.
    public void close() {
        if (this.published && !this.closed) {
            this.closed = true;
            this.closedDateTime = LocalDateTime.now();
        } else {
            throw new RuntimeException("스터디를 종료할 수 없습니다. 스터디를 공개하지 않았거나 이미 종료한 스터디입니다.");
        }
    }

    // 팀원 모집을 시작한다.
    // canUpdateRecruiting() 를 통과해야만 상태가 변경된다.
    // recruitingUpdatedDateTime 을 갱신해서 다음 1시간 동안 재변경을 막는다.
    public void startRecruit() {
        if (canUpdateRecruiting()) {
            this.recruiting = true;
            this.recruitingUpdatedDateTime = LocalDateTime.now();
        } else {
            throw new RuntimeException("인원 모집을 시작할 수 없습니다. 스터디를 공개하거나 한 시간 뒤 다시 시도하세요.");
        }
    }

    // 팀원 모집을 중단한다.
    public void stopRecruit() {
        if (canUpdateRecruiting()) {
            this.recruiting = false;
            this.recruitingUpdatedDateTime = LocalDateTime.now();
        } else {
            throw new RuntimeException("인원 모집을 멈출 수 없습니다. 스터디를 공개하거나 한 시간 뒤 다시 시도하세요.");
        }
    }

    /*
     * 관리자가 스터디를 강제 종료한다. 일반 close() 와 두 가지가 다르다.
     * <p>
     * 차이 1 — published 전제조건 없음
     * 일반 close() 는 "공개된 스터디만 종료할 수 있다" 는 규칙이 있다.
     * 소유자가 아직 공개도 안 한 스터디를 "종료" 하는 것은 의미가 없기 때문이다.
     * 그러나 관리자는 공개 전 스터디라도 부적절하다고 판단하면 차단할 수 있어야 한다.
     * 예: 회원가입 직후 욕설만 가득한 스터디를 만들고 공개 버튼만 안 누른 경우.
     * <p>
     * 차이 2 — 이미 종료된 스터디에는 예외를 던진다
     * closed=true 인 스터디를 또 종료하는 것은 의미가 없다.
     * 조용히 무시하고 성공 응답을 주면 "뭔가 된 것 같은데 아무 변화가 없다" 는
     * 혼란을 준다. 예외로 명확하게 실패를 알리는 것이 정직한 설계다.
     * <p>
     * [왜 '관리자에 의한 차단' 플래그를 따로 두지 않았는가]
     * 이번 설계에서는 일반 종료와 강제 종료의 최종 상태를 동일하게 둔다
     * (closed=true, published 는 그대로). 만약 나중에 "이 스터디는 관리자에 의해
     * 중단되었습니다" 같은 배너를 상세 페이지에 띄우고 싶어지면 그때
     * forcedClosed boolean 필드를 추가하면 된다. 이번엔 엔티티 변경을 최소화해
     * 기존 State Machine 의 불변식을 최대한 유지한다.
     * <p>
     * [왜 publishedDateTime 은 건드리지 않는가]
     * 공개된 적이 있는 스터디라면 그 시점의 기록은 보존하는 것이 맞다.
     * "공개됐었지만 관리자에 의해 종료됨" 이라는 이력이 둘 다 의미 있는 정보이기 때문.
     * published=false 로 되돌리지 않는 이유도 같다. Option A 의 핵심이다.
     *
     * @throws RuntimeException 이미 종료된 스터디를 강제 종료 시도할 때
     */
    public void forceClose() {
        if (this.closed) {
            throw new RuntimeException("이미 종료된 스터디는 다시 종료할 수 없습니다.");
        }
        this.closed = true;
        this.closedDateTime = LocalDateTime.now();
    }

    // =====================================================================
    // 유틸리티 메서드
    // =====================================================================

    // 스터디를 삭제할 수 있는지 확인한다.
    // 공개(published=true) 된 이후에는 삭제를 허용하지 않는다.
    // 이미 공개된 스터디를 삭제하면 멤버들에게 혼란을 줄 수 있으므로 제한한다.
    public boolean isRemovable() {
        return !this.published;
    }

    // path 를 URL 에서 안전하게 사용할 수 있도록 UTF-8 인코딩한다.
    // path 에 한글이 포함된 경우("스프링-스터디") URL 에 그대로 쓰면 깨질 수 있다.
    // URLEncoder 는 한글을 "%EC%8A%A4..." 형태로 변환해 URL 에서 안전하게 쓸 수 있게 한다.
    public String getEncodedPath() {
        return URLEncoder.encode(this.path, StandardCharsets.UTF_8);
    }
}

/**
 * [@ElementCollection 의 동작 원리]
 *
 * @ElementCollection 은 엔티티가 아닌 단순 값(여기서는 Long ID)의 컬렉션을
 * 별도 테이블에 저장하는 방법이다.
 *
 * study_tag_ids 테이블의 구조:
 *   study_id  |  tag_id
 *   --------- | -------
 *   1         |  10
 *   1         |  25
 *   2         |  10
 *
 * Study 를 조회할 때 tagIds 에 접근하면 JPA 가 자동으로
 *   SELECT tag_id FROM study_tag_ids WHERE study_id = ?
 * 쿼리를 실행해 Set<Long> 을 채워준다.
 *
 * tagIds.add(tagId) 를 호출하면 JPA 가 트랜잭션 종료 시점에
 *   INSERT INTO study_tag_ids (study_id, tag_id) VALUES (?, ?)
 * 를 자동으로 실행한다(Dirty Checking 으로).
 *
 *
 * [Dirty Checking(변경 감지) 이란?]
 *
 * JPA 트랜잭션 안에서 Repository.findById() 등으로 조회한 엔티티는
 * "영속 상태(Managed State)" 가 된다.
 * JPA 는 이 엔티티의 조회 시점 상태를 "스냅샷"으로 기억해 둔다.
 * 트랜잭션이 커밋되는 순간 현재 상태와 스냅샷을 비교해서(dirty checking)
 * 바뀐 필드가 있으면 자동으로 UPDATE 쿼리를 실행한다.
 *
 * 예: 서비스에서 study.publish() 를 호출하면 published=true, publishedDateTime=now 로 바뀐다.
 * 명시적으로 studyRepository.save(study) 를 호출하지 않아도
 * @Transactional 이 붙은 서비스 메서드가 끝나면 JPA 가 변경을 감지하고
 * UPDATE study SET published=true, published_date_time=? WHERE id=? 를 실행한다.
 *
 *
 * [Detached(분리) 상태란?]
 *
 * 트랜잭션이 종료되면 영속 상태였던 엔티티는 "분리 상태(Detached State)" 가 된다.
 * 분리 상태에서는 JPA 가 더 이상 이 객체를 추적하지 않는다.
 * 즉, 필드를 변경해도 DB 에 반영되지 않는다.
 *
 * @Transactional 없이 Repository.findById() 로 조회하면 바로 분리 상태가 된다.
 * 이 경우 변경사항을 DB 에 반영하려면 repository.save() 를 명시적으로 호출해야 한다.
 *
 * 또한 분리 상태에서 LAZY 로딩된 컬렉션(tagIds 등)에 접근하면
 * LazyInitializationException 이 발생한다.
 * 이 오류가 뜨면 "트랜잭션 밖에서 LAZY 필드에 접근했구나" 라고 이해하면 된다.
 *
 *
 * [@Builder.Default 가 필요한 이유]
 *
 * Lombok @Builder 는 내부적으로 빌더 클래스를 생성하는데,
 * 필드 선언부의 초기화 표현식(= new HashSet<>())을 빌더가 무시하는 문제가 있다.
 * 결과적으로 빌더로 생성한 Study 에서 tagIds 는 null 이 된다.
 * @Builder.Default 를 붙이면 빌더로 값을 지정하지 않았을 때 초기화 표현식의 값을 쓴다.
 *
 *
 * [State Machine 패턴 — 상태 전이를 메서드로 캡슐화하는 이유]
 *
 * study.setPublished(true) 와 study.setPublishedDateTime(LocalDateTime.now()) 를
 * 서비스에서 직접 호출하면 두 가지 문제가 생긴다.
 *
 * 첫째, publishedDateTime 설정을 빠뜨릴 수 있다.
 * 둘째, 이미 종료된 스터디를 다시 공개하는 잘못된 상태 전이를 막을 수 없다.
 *
 * publish(), close(), startRecruit(), stopRecruit() 메서드로 캡슐화하면
 * 조건 검사 + 상태 변경 + 시각 기록이 항상 함께 실행되도록 강제할 수 있고,
 * 허용되지 않는 상태 전이는 예외를 던져 즉시 알려준다.
 */