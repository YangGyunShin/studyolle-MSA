package com.studyolle.study.service;

import com.studyolle.study.dto.request.CreateStudyRequest;
import com.studyolle.study.entity.JoinRequest;
import com.studyolle.study.entity.JoinRequestStatus;
import com.studyolle.study.entity.Study;
import com.studyolle.study.repository.JoinRequestRepository;
import com.studyolle.study.repository.StudyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * 스터디 핵심 비즈니스 로직 담당 서비스.
 *
 * =============================================
 * 서비스 분리 원칙
 * =============================================
 *
 * StudyController 가 사용하는 메서드 → 이 클래스(StudyService)
 * StudySettingsController 가 사용하는 메서드 → StudySettingsService
 *
 * 하나의 서비스가 너무 많은 일을 하면 파일이 수천 줄이 되어 관리하기 어렵다.
 * "스터디 조회/가입/생성"과 "스터디 설정 변경"은 목적이 다르므로 클래스를 분리했다.
 * 이것이 단일 책임 원칙(SRP, Single Responsibility Principle)이다.
 *
 * =============================================
 * @Transactional 의 동작 방식
 * =============================================
 *
 * 클래스 레벨에 @Transactional 을 선언하면 모든 public 메서드가
 * 트랜잭션 안에서 실행된다.
 *
 * 트랜잭션이란 "하나의 작업 단위"를 뜻한다.
 * 메서드가 정상적으로 끝나면 커밋(DB 에 반영),
 * 예외가 발생하면 롤백(변경 사항 취소)된다.
 *
 * 읽기 전용 메서드에는 @Transactional(readOnly = true) 를 따로 선언한다.
 * 이렇게 하면 JPA 의 Dirty Checking(변경 감지)이 비활성화되어 성능이 향상된다.
 * 자세한 내용은 파일 하단 블록 주석을 참고한다.
 *
 * =============================================
 * Dirty Checking 기반 자동 저장
 * =============================================
 *
 * addMember(), removeMember() 등에서 명시적 save() 가 없어도
 * 변경 사항이 DB 에 자동으로 반영된다.
 * 이것이 JPA 의 Dirty Checking 이다. 자세한 내용은 하단 블록 주석을 참고한다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class StudyService {

    private final StudyRepository studyRepository;
    private final JoinRequestRepository joinRequestRepository;

    // ============================
    // 스터디 생성
    // ============================

    /**
     * 새로운 스터디를 생성하고, 요청한 사용자를 관리자(Manager)로 등록한다.
     *
     * path 중복 검증을 먼저 수행한 뒤, 빌더 패턴으로 Study 를 생성한다.
     * 생성자가 곧 첫 번째 관리자이므로 study.addManager(accountId) 로 바로 등록한다.
     */
    public Study createNewStudy(CreateStudyRequest request, Long accountId) {

        // path 중복 검증 — 같은 경로의 스터디가 이미 있으면 예외를 던진다
        if (studyRepository.existsByPath(request.getPath())) {
            throw new IllegalArgumentException("이미 사용 중인 스터디 경로입니다: " + request.getPath());
        }

        Study study = Study.builder()
                .path(request.getPath())
                .title(request.getTitle())
                .shortDescription(request.getShortDescription())
                .fullDescription(request.getFullDescription())
                .joinType(request.getJoinType())
                .build();

        study.addManager(accountId); // 스터디를 만든 사람이 첫 번째 관리자

        return studyRepository.save(study);

        // [Phase 5 TODO]
        // StudyCreatedEvent 발행 → notification-service 가 RabbitMQ 로 수신
        // eventPublisher.publishEvent(new StudyCreatedEvent(study));
    }

    // ============================
    // 스터디 조회
    // ============================

    /**
     * path 로 스터디를 조회한다. 없으면 예외를 던진다.
     *
     * 내부적으로 여러 메서드가 이 메서드를 호출하는 공통 조회 로직이다.
     * null 반환 대신 예외를 던지는 이유: 호출한 쪽에서 null 체크를 잊으면
     * NullPointerException 이 엉뚱한 곳에서 발생하기 때문이다.
     * 예외를 던지면 문제의 원인을 즉시 파악할 수 있다.
     */
    public Study getStudy(String path) {
        Study study = studyRepository.findByPath(path);
        if (study == null) {
            throw new IllegalArgumentException("'" + path + "' 에 해당하는 스터디가 없습니다.");
        }
        return study;
    }

    /**
     * path 로 스터디를 조회하고, 요청자가 관리자인지 검증한다.
     *
     * 스터디 설정 페이지처럼 관리자만 접근할 수 있는 기능에서 호출한다.
     * 조회 + 권한 검증을 한 번에 처리하므로 컨트롤러 코드가 간결해진다.
     *
     * readOnly = true: 권한 확인만 하고 데이터를 변경하지 않으므로 읽기 전용 트랜잭션으로 실행한다.
     */
    @Transactional(readOnly = true)
    public Study getStudyToUpdate(Long accountId, String path) {
        Study study = getStudy(path);
        checkIfManager(study, accountId);
        return study;
    }

    /**
     * Study 기본 컬럼만 조회하고, 요청자가 관리자인지 검증한다.
     *
     * publish/close/recruit 상태 변경처럼 tagIds, zoneIds 등 컬렉션이 필요 없는 작업에 사용한다.
     * findByPath 대신 findStudyOnlyByPath 를 사용하면 컬렉션 테이블을 건드리지 않아 쿼리가 가볍다.
     */
    @Transactional(readOnly = true)
    public Study getStudyToUpdateStatus(Long accountId, String path) {
        Study study = studyRepository.findStudyOnlyByPath(path);
        if (study == null) {
            throw new IllegalArgumentException("'" + path + "' 에 해당하는 스터디가 없습니다.");
        }
        checkIfManager(study, accountId);
        return study;
    }

    // ============================
    // 멤버 가입/탈퇴
    // ============================

    /**
     * OPEN 방식 가입 처리: 즉시 memberIds 에 추가한다.
     *
     * isJoinable() 은 Study 엔티티가 담당한다.
     * "가입 가능 여부"는 스터디 도메인의 규칙이므로 엔티티 안에 로직을 두는 것이 적합하다.
     * 서비스는 엔티티에 위임하고, 결과에 따라 예외만 처리한다.
     *
     * save() 를 호출하지 않아도 Dirty Checking 으로 memberIds 변경이 자동 반영된다.
     */
    public void addMember(Study study, Long accountId) {
        if (!study.isJoinable(accountId)) {
            throw new IllegalStateException("가입할 수 없는 스터디입니다.");
        }
        study.addMember(accountId);
    }

    /**
     * 스터디 탈퇴 처리: memberIds 에서 제거한다.
     */
    public void removeMember(Study study, Long accountId) {
        study.removeMember(accountId);
    }

    // ============================
    // 승인제 가입 신청
    // ============================

    /**
     * APPROVAL_REQUIRED 방식 가입 신청을 생성한다.
     *
     * 같은 스터디에 PENDING 상태 신청이 이미 있으면 중복 신청으로 예외를 던진다.
     *
     * accountNickname 을 파라미터로 받는 이유:
     * JoinRequest 에 닉네임을 비정규화해서 저장하기 때문이다.
     * 신청 목록을 보여줄 때마다 account-service 를 호출하지 않아도 되므로 효율적이다.
     * (JoinRequest 비정규화 설계에 대한 자세한 내용은 JoinRequestRepository 하단 주석 참고)
     *
     * @param study          가입 신청할 스터디
     * @param accountId      신청자 ID
     * @param accountNickname 신청자 닉네임 (비정규화 저장용)
     */
    public JoinRequest createJoinRequest(Study study, Long accountId, String accountNickname) {
        boolean alreadyRequested = joinRequestRepository.existsByStudyAndAccountIdAndStatus(
                study, accountId, JoinRequestStatus.PENDING);
        if (alreadyRequested) {
            throw new IllegalStateException("이미 가입 신청 중입니다.");
        }
        return joinRequestRepository.save(
                JoinRequest.createRequest(accountId, accountNickname, study));
    }

    /**
     * 가입 신청을 승인한다: PENDING → APPROVED, memberIds 에 추가.
     *
     * joinRequest.approve() 가 상태 변경을 담당하고,
     * 승인 후 즉시 memberIds 에도 추가한다.
     * 두 작업이 같은 트랜잭션 안에서 실행되므로 둘 중 하나만 성공하는 상황이 생기지 않는다.
     */
    public void approveJoinRequest(Long joinRequestId) {
        JoinRequest joinRequest = getJoinRequest(joinRequestId);
        if (!joinRequest.isPending()) {
            throw new IllegalStateException("대기 중인 신청만 승인할 수 있습니다.");
        }
        joinRequest.approve();
        joinRequest.getStudy().addMember(joinRequest.getAccountId()); // Dirty Checking 으로 자동 반영
    }

    /**
     * 가입 신청을 거절한다: PENDING → REJECTED.
     */
    public void rejectJoinRequest(Long joinRequestId) {
        JoinRequest joinRequest = getJoinRequest(joinRequestId);
        if (!joinRequest.isPending()) {
            throw new IllegalStateException("대기 중인 신청만 거절할 수 있습니다.");
        }
        joinRequest.reject();
    }

    /**
     * 대기 중인 가입 신청이 있는지 확인한다.
     *
     * 뷰에서 "신청 중" / "신청 취소" 버튼 표시 여부를 결정할 때 사용한다.
     * 데이터를 변경하지 않으므로 readOnly = true.
     */
    @Transactional(readOnly = true)
    public boolean hasPendingJoinRequest(Study study, Long accountId) {
        return joinRequestRepository.existsByStudyAndAccountIdAndStatus(
                study, accountId, JoinRequestStatus.PENDING);
    }

    // ============================
    // 검색 & 추천
    // ============================

    /**
     * 키워드 기반 스터디 검색 (페이징).
     *
     * 이 메서드가 직접 쿼리를 작성하지 않고 StudyRepository.findByKeyword() 에 위임한다.
     * 복잡한 QueryDSL 쿼리는 Repository 계층의 책임이고,
     * 서비스는 "무엇을 할지"만 결정한다. (계층 분리)
     *
     * tagIds, zoneIds 를 파라미터로 받는 이유:
     * 컨트롤러(또는 서비스)가 먼저 MetadataFeignClient 로 keyword 와 매칭되는 태그/지역 ID 를 받아오고,
     * 그 ID 목록을 이 메서드에 전달한다. study-service DB 에는 ID 만 저장되어 있기 때문이다.
     */
    @Transactional(readOnly = true)
    public Page<Study> searchStudies(String keyword, Set<Long> tagIds, Set<Long> zoneIds,
                                     Pageable pageable, boolean recruiting, boolean open) {
        return studyRepository.findByKeyword(keyword, tagIds, zoneIds, pageable, recruiting, open);
    }

    /**
     * 사용자 관심사(태그/지역 ID) 기반 스터디 추천.
     *
     * 관심사가 전혀 없는 경우(새 회원 등)에는 최신 공개 스터디 9개를 대신 반환한다.
     * 빈 IN 조건으로 QueryDSL 에서 오류가 발생하는 것을 방지하는 역할도 겸한다.
     */
    @Transactional(readOnly = true)
    public List<Study> getRecommendedStudies(Set<Long> tagIds, Set<Long> zoneIds) {
        if ((tagIds == null || tagIds.isEmpty()) && (zoneIds == null || zoneIds.isEmpty())) {
            return studyRepository.findFirst9ByPublishedAndClosedOrderByPublishedDateTimeDesc(true, false);
        }
        return studyRepository.findByAccount(tagIds, zoneIds);
    }

    // ============================
    // 대시보드용 스터디 목록
    // ============================

    /**
     * 관리자로 참여 중인 활동 중인 스터디 최대 5개 (대시보드용).
     */
    @Transactional(readOnly = true)
    public List<Study> getStudiesAsManager(Long accountId) {
        return studyRepository
                .findFirst5ByManagerIdsContainingAndClosedOrderByPublishedDateTimeDesc(accountId, false);
    }

    /**
     * 멤버로 참여 중인 활동 중인 스터디 최대 5개 (대시보드용).
     */
    @Transactional(readOnly = true)
    public List<Study> getStudiesAsMember(Long accountId) {
        return studyRepository
                .findFirst5ByMemberIdsContainingAndClosedOrderByPublishedDateTimeDesc(accountId, false);
    }

    // ============================
    // 스터디 삭제
    // ============================

    /**
     * 스터디를 삭제한다.
     *
     * isRemovable() 은 Study 엔티티가 담당한다.
     * 삭제 가능 조건(공개 여부 등)은 도메인 규칙이므로 엔티티 안에 두는 것이 적합하다.
     */
    public void remove(Study study) {
        if (!study.isRemovable()) {
            throw new IllegalStateException("공개된 스터디는 삭제할 수 없습니다.");
        }
        studyRepository.delete(study);
    }

    // ============================
    // 내부 헬퍼 메서드
    // ============================

    /**
     * 요청자가 스터디 관리자인지 확인한다. 아니면 예외를 던진다.
     *
     * private 메서드로 선언하여 이 클래스 내부에서만 사용하도록 제한한다.
     * getStudyToUpdate, getStudyToUpdateStatus 두 메서드가 동일한 검증 로직을 사용하므로
     * 중복을 피하기 위해 별도 메서드로 추출했다.
     */
    private void checkIfManager(Study study, Long accountId) {
        if (!study.isManagerOf(accountId)) {
            throw new IllegalStateException("스터디 관리자만 접근할 수 있습니다.");
        }
    }

    /**
     * joinRequestId 로 JoinRequest 를 조회한다. 없으면 예외를 던진다.
     */
    private JoinRequest getJoinRequest(Long joinRequestId) {
        return joinRequestRepository.findById(joinRequestId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "가입 신청을 찾을 수 없습니다. id=" + joinRequestId));
    }
}

/*
 * ============================================================
 * [JPA Dirty Checking 심층 설명]
 * ============================================================
 *
 * 1. Dirty Checking 이란?
 * ------------------------------------------------------------
 * "Dirty" 는 "변경된 상태"를 뜻한다.
 * JPA 는 트랜잭션 시작 시점에 조회한 엔티티의 초기 상태를 "스냅샷"으로 저장해둔다.
 * 트랜잭션이 끝나는 시점(커밋 직전)에 현재 상태와 스냅샷을 비교(Dirty Check)하여
 * 변경된 필드가 있으면 자동으로 UPDATE 쿼리를 생성해서 DB 에 반영한다.
 *
 * 즉, 이 코드에서:
 *
 *   study.addMember(accountId);
 *   // save() 호출 없음
 *
 * study 는 studyRepository.findByPath() 로 조회된 "영속 상태"의 엔티티다.
 * addMember() 로 memberIds 컬렉션을 변경하면 JPA 가 변경을 감지하고
 * 트랜잭션 커밋 시 INSERT INTO study_member_ids ... 쿼리를 자동으로 날린다.
 *
 *
 * 2. 영속 상태란?
 * ------------------------------------------------------------
 * JPA 엔티티는 세 가지 상태 중 하나에 있다:
 *
 *   비영속(Transient)   : new Study() 처럼 막 생성된 상태. JPA 가 관리하지 않는다.
 *   영속(Persistent)    : EntityManager(JPA 의 핵심 객체)가 관리하는 상태.
 *                         Repository 로 조회하거나 save() 하면 이 상태가 된다.
 *   준영속/분리(Detached): 트랜잭션이 끝난 후의 상태. JPA 가 더 이상 추적하지 않는다.
 *
 * Dirty Checking 은 "영속 상태"의 엔티티에만 동작한다.
 * 그래서 findByPath() 로 조회한 study 는 영속 상태이므로 변경 감지가 작동하고,
 * new Study() 로 만든 객체는 비영속 상태이므로 반드시 save() 를 호출해야 한다.
 * createNewStudy() 에서 studyRepository.save(study) 를 명시적으로 호출하는 이유다.
 *
 *
 * 3. readOnly = true 일 때 Dirty Checking 이 꺼지는 이유
 * ------------------------------------------------------------
 * JPA 는 영속 상태 엔티티의 스냅샷을 유지하기 위해 메모리를 사용한다.
 * 트랜잭션이 끝날 때 스냅샷과 현재 상태를 비교하는 연산도 수행한다.
 *
 * @Transactional(readOnly = true) 를 선언하면 Hibernate 가 스냅샷을 아예 만들지 않는다.
 * 비교 연산도 생략된다. 조회만 하는 메서드에서는 불필요한 메모리와 연산을 절약할 수 있다.
 *
 * 또한 읽기 전용 트랜잭션에서 엔티티를 변경해도 DB 에 반영되지 않는 안전장치 역할도 한다.
 *
 *
 * 4. 클래스 레벨 @Transactional 과 메서드 레벨 @Transactional(readOnly = true) 의 우선순위
 * ------------------------------------------------------------
 * 이 클래스는 아래와 같이 선언되어 있다:
 *
 *   @Transactional              // 클래스 레벨: 모든 메서드의 기본값
 *   public class StudyService {
 *
 *       @Transactional(readOnly = true)  // 메서드 레벨: 이 메서드만 덮어씀
 *       public Study getStudyToUpdate(...) { ... }
 *   }
 *
 * 메서드 레벨 선언이 클래스 레벨 선언보다 우선한다.
 * getStudyToUpdate() 는 readOnly = true 로 실행되고,
 * createNewStudy() 같은 나머지 메서드는 클래스 레벨의 readOnly = false(기본값)로 실행된다.
 */