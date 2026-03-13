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
 * StudyService — 스터디 핵심 비즈니스 로직 담당
 * <p>
 * =============================================
 * [모노리틱 참조: StudyService.java]
 * =============================================
 * <p>
 * [서비스 분리 배경 — 모노리틱과 동일한 이유]
 * StudyController 가 사용하는 메서드 → StudyService
 * StudySettingsController 가 사용하는 메서드 → StudySettingsService
 * account 모듈의 AccountService / AccountSettingsService 분리와 동일한 패턴.
 * <p>
 * =============================================
 * [모노리틱과의 주요 변경사항]
 * =============================================
 * <p>
 * [제거]
 * - ApplicationEventPublisher: Phase 5 RabbitMQ 로 대체 예정.
 * StudyCreatedEvent, StudyUpdateEvent 발행 코드 전부 제거.
 * - StandardServletMultipartResolver: 불필요한 의존성 제거.
 * - Account 파라미터 전체: Long accountId 로 교체.
 * <p>
 * [변경]
 * - createNewStudy(Study, Account) → createNewStudy(CreateStudyRequest, Long accountId)
 * ModelMapper 없이 직접 빌더 패턴으로 Study 생성 (명시적으로 더 안전)
 * - addMember(Study, Account) → addMember(Study, Long accountId)
 * - removeMember(Study, Account) → removeMember(Study, Long accountId)
 * - createJoinRequest(Study, Account) → createJoinRequest(Study, Long accountId, String nickname)
 * - getStudiesAsManager(Account) → getStudiesAsManager(Long accountId)
 * - getStudiesAsMember(Account) → getStudiesAsMember(Long accountId)
 * <p>
 * =============================================
 * JPA Dirty Checking 기반 자동 반영 — 모노리틱과 동일
 * =============================================
 * <p>
 * addMember(), removeMember() 등에서 명시적 save() 가 없는 이유:
 * Repository 를 통해 조회된 영속 상태의 Study 에서 컬렉션을 변경하면,
 *
 * @Transactional 범위 내 커밋 시 JPA 가 자동으로 DB 에 반영한다.
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
     * <p>
     * [모노리틱 변경]
     * createNewStudy(Study study, Account account)
     * → createNewStudy(CreateStudyRequest request, Long accountId)
     * <p>
     * ModelMapper 대신 빌더 패턴으로 직접
     * Study 를 생성하는 이유: 명시적이고 컴파일 타임에 안전하다.
     */
    public Study createNewStudy(CreateStudyRequest request, Long accountId) {

        // path 중복 검증 (모노리틱의 StudyFormValidator 역할)
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

        // 생성자를 관리자로 등록
        study.addManager(accountId);

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
     * <p>
     * [모노리틱 참조: StudyService.getStudy(path)]
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
     * <p>
     * [모노리틱 참조: StudyService.getStudyToUpdate(account, path)]
     * 컨트롤러에서 권한 검증을 위해 가장 많이 호출되는 메서드.
     */
    @Transactional(readOnly = true)
    public Study getStudyToUpdate(Long accountId, String path) {
        Study study = getStudy(path);
        checkIfManager(study, accountId);
        return study;
    }

    /**
     * 읽기 전용 — 컬렉션(tagIds, zoneIds 등) 없이 Study 기본 필드만 조회.
     * <p>
     * [모노리틱 참조: StudyService.getStudyToUpdateStatus(account, path)]
     * publish/close/recruit 상태 변경처럼 컬렉션 데이터가 필요 없는 작업에 사용.
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
     * <p>
     * [모노리틱 참조: StudyService.addMember(study, account)]
     */
    public void addMember(Study study, Long accountId) {
        if (!study.isJoinable(accountId)) {
            throw new IllegalStateException("가입할 수 없는 스터디입니다.");
        }
        study.addMember(accountId);
    }

    /**
     * 스터디 탈퇴 처리: memberIds 에서 제거한다.
     * <p>
     * [모노리틱 참조: StudyService.removeMember(study, account)]
     */
    public void removeMember(Study study, Long accountId) {
        study.removeMember(accountId);
    }

    // ============================
    // 승인제 가입 신청
    // ============================

    /**
     * APPROVAL_REQUIRED 방식 가입 신청 생성.
     * <p>
     * [모노리틱 참조: StudyService.createJoinRequest(study, account)]
     * [변경]
     * - Account → Long accountId + String accountNickname
     * - accountNickname 은 알림용 비정규화 필드 (Phase 5 RabbitMQ 메시지에서 사용)
     * <p>
     * 중복 신청 방지: 이미 PENDING 상태의 신청이 있으면 예외를 던진다.
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
     * 가입 신청 승인: PENDING → APPROVED, memberIds 에 추가.
     * <p>
     * [모노리틱 참조: StudyService.approveJoinRequest(joinRequestId)]
     */
    public void approveJoinRequest(Long joinRequestId) {
        JoinRequest joinRequest = getJoinRequest(joinRequestId);
        if (!joinRequest.isPending()) {
            throw new IllegalStateException("대기 중인 신청만 승인할 수 있습니다.");
        }
        joinRequest.approve();
        joinRequest.getStudy().addMember(joinRequest.getAccountId());
    }

    /**
     * 가입 신청 거절: PENDING → REJECTED.
     * <p>
     * [모노리틱 참조: StudyService.rejectJoinRequest(joinRequestId)]
     */
    public void rejectJoinRequest(Long joinRequestId) {
        JoinRequest joinRequest = getJoinRequest(joinRequestId);
        if (!joinRequest.isPending()) {
            throw new IllegalStateException("대기 중인 신청만 거절할 수 있습니다.");
        }
        joinRequest.reject();
    }

    /**
     * 대기 중인 가입 신청이 있는지 확인한다. (뷰에서 "신청 취소" 버튼 표시 여부)
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
     * <p>
     * [2단계 검색 흐름]
     * 1단계: StudySettingsController → MetadataFeignClient.findTagIdsByKeyword(keyword)
     * MetadataFeignClient.findZoneIdsByKeyword(keyword) 를 먼저 호출
     * 2단계: 반환된 tagIds, zoneIds 와 keyword 를 이 메서드에 전달
     * <p>
     * [이 메서드는 QueryDSL 에 위임]
     */
    @Transactional(readOnly = true)
    public Page<Study> searchStudies(String keyword, Set<Long> tagIds, Set<Long> zoneIds,
                                     Pageable pageable, boolean recruiting, boolean open) {
        return studyRepository.findByKeyword(keyword, tagIds, zoneIds, pageable, recruiting, open);
    }

    /**
     * 사용자 관심사(태그/지역) 기반 스터디 추천.
     * <p>
     * [모노리틱 참조: MainController 에서 StudyRepository.findByAccount 를 직접 호출]
     * MSA 에서는 Controller → Service → Repository 계층을 준수한다.
     */
    @Transactional(readOnly = true)
    public List<Study> getRecommendedStudies(Set<Long> tagIds, Set<Long> zoneIds) {
        if ((tagIds == null || tagIds.isEmpty()) && (zoneIds == null || zoneIds.isEmpty())) {
            // 관심사가 없으면 최신 공개 스터디를 반환
            return studyRepository.findFirst9ByPublishedAndClosedOrderByPublishedDateTimeDesc(true, false);
        }
        return studyRepository.findByAccount(tagIds, zoneIds);
    }

    // ============================
    // 대시보드용 스터디 목록
    // ============================

    /**
     * 관리자로 참여 중인 활동 중인 스터디 5개 (대시보드)
     */
    @Transactional(readOnly = true)
    public List<Study> getStudiesAsManager(Long accountId) {
        return studyRepository
                .findFirst5ByManagerIdsContainingAndClosedOrderByPublishedDateTimeDesc(accountId, false);
    }

    /**
     * 멤버로 참여 중인 활동 중인 스터디 5개 (대시보드)
     */
    @Transactional(readOnly = true)
    public List<Study> getStudiesAsMember(Long accountId) {
        return studyRepository
                .findFirst5ByMemberIdsContainingAndClosedOrderByPublishedDateTimeDesc(accountId, false);
    }

    // ============================
    // 상태 변경
    // ============================

    /**
     * 스터디 삭제
     */
    public void remove(Study study) {
        if (!study.isRemovable()) {
            throw new IllegalStateException("공개된 스터디는 삭제할 수 없습니다.");
        }
        studyRepository.delete(study);
    }

    // ============================
    // 내부 헬퍼
    // ============================

    private void checkIfManager(Study study, Long accountId) {
        if (!study.isManagerOf(accountId)) {
            throw new IllegalStateException("스터디 관리자만 접근할 수 있습니다.");
        }
    }

    private JoinRequest getJoinRequest(Long joinRequestId) {
        return joinRequestRepository.findById(joinRequestId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "가입 신청을 찾을 수 없습니다. id=" + joinRequestId));
    }
}