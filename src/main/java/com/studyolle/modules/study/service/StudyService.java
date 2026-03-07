package com.studyolle.modules.study.service;

import com.studyolle.modules.account.entity.Account;
import com.studyolle.modules.study.entity.JoinRequest;
import com.studyolle.modules.study.entity.JoinRequestStatus;
import com.studyolle.modules.study.entity.Study;
import com.studyolle.modules.study.event.StudyCreatedEvent;
import com.studyolle.modules.study.event.StudyUpdateEvent;
import com.studyolle.modules.study.repository.JoinRequestRepository;
import com.studyolle.modules.study.repository.StudyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;

import java.util.List;

/**
 * StudyService - 스터디 핵심 비즈니스 로직 담당
 * <p>
 * =============================================
 * 서비스 분리 배경 (Controller-based Service Splitting)
 * =============================================
 * <p>
 * 기존 StudyService는 StudyController와 StudySettingsController 두 컨트롤러가
 * 필요로 하는 모든 비즈니스 로직을 하나의 클래스에 담고 있었습니다.
 * 이는 account 모듈에서 AccountService와 SettingsService를 분리한 것과 동일한 이유로,
 * 단일 책임 원칙(SRP)과 코드 응집도 향상을 위해 분리되었습니다.
 * <p>
 * =============================================
 * 분리 기준
 * =============================================
 * <p>
 * StudyService (이 클래스)
 * - StudyController가 사용하는 메서드들
 * - 스터디 생성, 기본 조회, 멤버 가입/탈퇴
 * - 모임(Event) 생성시 스터디 조회 (getStudyToEnroll)
 * <p>
 * StudySettingsService (분리된 클래스)
 * - StudySettingsController가 사용하는 메서드들
 * - 소개/배너 수정, 태그/지역 관리, 상태 관리(공개/종료/모집)
 * - 경로/제목 변경, 스터디 삭제
 * <p>
 * =============================================
 * getStudyWithMembersByPath 메서드 추가 배경
 * =============================================
 * <p>
 * 기존 StudyController의 joinStudy(), leaveStudy() 메서드에서
 * studyRepository.findStudyWithMembersByPath(path)를 직접 호출하는
 * 계층 위반(Controller -> Repository 직접 접근)이 있었습니다.
 * <p>
 * Controller -> Service -> Repository 원칙을 준수하기 위해
 * 해당 Repository 호출을 이 Service 메서드로 감싸서 제공합니다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class StudyService {

    private final StudyRepository studyRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final JoinRequestRepository joinRequestRepository;
    private final StandardServletMultipartResolver standardServletMultipartResolver;

    // ============================
    // 스터디 생성
    // ============================

    /**
     * 새로운 스터디를 생성하고, 요청한 사용자를 관리자(Manager)로 등록합니다.
     * <p>
     * 생성 흐름:
     * 1. Study 엔티티를 DB에 저장 (영속 상태 전환)
     * 2. 요청자를 해당 스터디의 관리자로 등록
     *
     * @param study   ModelMapper로 StudyForm에서 변환된 Study 엔티티
     * @param account 스터디를 생성하는 현재 로그인 사용자
     * @return 저장된 Study 엔티티 (영속 상태)
     */
    public Study createNewStudy(Study study, Account account) {
        Study newStudy = studyRepository.save(study);
        newStudy.addManager(account);
        return newStudy;
    }

    // ============================
    // 스터디 조회
    // ============================

    /**
     * path를 기반으로 스터디를 조회합니다.
     * 존재하지 않는 경우 IllegalArgumentException이 발생합니다.
     * <p>
     * 이 메서드는 @EntityGraph(attributePaths = {"tags", "zones", "managers", "members"})가
     * 적용된 findByPath를 사용하므로, 모든 연관 엔티티를 함께 로딩합니다.
     * 주로 스터디 상세 페이지(viewStudy)나 멤버 목록 페이지에서 사용됩니다.
     *
     * @param path 스터디 URL 경로 (고유 식별자)
     * @return 조회된 Study 엔티티
     * @throws IllegalArgumentException path에 해당하는 스터디가 없는 경우
     */
    public Study getStudy(String path) {
        Study study = studyRepository.findByPath(path);
        checkIfExistingStudy(path, study);
        return study;
    }

    /**
     * members 연관관계를 함께 로딩하여 스터디를 조회합니다.
     * <p>
     * =============================================
     * 이 메서드가 필요한 이유 (계층 위반 수정)
     * =============================================
     * <p>
     * 기존 StudyController의 joinStudy(), leaveStudy()에서는
     * studyRepository.findStudyWithMembersByPath(path)를 직접 호출했습니다.
     * 이는 Controller -> Repository 직접 접근이라는 계층 위반에 해당합니다.
     * <p>
     * Controller는 Service 계층을 통해서만 데이터에 접근해야 하므로,
     * Repository 호출을 이 Service 메서드로 감싸서 계층 원칙을 준수합니다.
     * <p>
     * 호출 흐름 변경:
     * [기존] StudyController -> StudyRepository.findStudyWithMembersByPath()
     * [수정] StudyController -> StudyService.getStudyWithMembersByPath() -> StudyRepository
     *
     * @param path 스터디 URL 경로
     * @return members가 fetch join된 Study 엔티티
     * @throws IllegalArgumentException path에 해당하는 스터디가 없는 경우
     */
    public Study getStudyWithMembersByPath(String path) {
        Study study = studyRepository.findStudyWithMembersByPath(path);
        checkIfExistingStudy(path, study);
        return study;
    }

    /**
     * 모임(Event) 참여를 위한 스터디 조회 메서드.
     * <p>
     * 연관 엔티티를 fetch join 하지 않는 최소 조회 전략을 사용합니다.
     * 모임 등록/수정 시 스터디의 존재 여부만 확인하면 되므로,
     * tags, zones, members 등의 부가 정보는 로딩하지 않아 성능을 최적화합니다.
     *
     * @param path 스터디 URL 경로
     * @return 기본 정보만 로딩된 Study 엔티티
     * @throws IllegalArgumentException path에 해당하는 스터디가 없는 경우
     */
    public Study getStudyToEnroll(String path) {
        Study study = studyRepository.findStudyOnlyByPath(path);
        checkIfExistingStudy(path, study);
        return study;
    }

    // ============================
    // 멤버 관리 (가입 / 탈퇴)
    // ============================

    /**
     * 스터디에 멤버를 추가합니다.
     * <p>
     * study.addMember(account)만 호출하고 별도의 save()를 호출하지 않습니다.
     * 이는 JPA의 영속성 컨텍스트와 Dirty Checking 덕분입니다.
     * <p>
     * Study 객체는 Controller에서 조회된 시점에 이미 영속 상태이므로,
     *
     * @param study   영속 상태의 Study 엔티티 (members가 로딩된 상태)
     * @param account 가입할 사용자
     * @Transactional 범위 내에서 필드를 변경하면 트랜잭션 커밋 시점에
     * JPA가 변경 사항을 감지하여 자동으로 UPDATE/INSERT SQL을 실행합니다.
     */
    public void addMember(Study study, Account account) {
        study.addMember(account);
    }

    /**
     * 스터디에서 멤버를 제거합니다.
     * <p>
     * addMember()와 동일하게, JPA Dirty Checking에 의해
     * 트랜잭션 커밋 시 자동으로 DELETE SQL이 실행됩니다.
     *
     * @param study   영속 상태의 Study 엔티티 (members가 로딩된 상태)
     * @param account 탈퇴할 사용자
     */
    public void removeMember(Study study, Account account) {
        study.removeMember(account);
    }

    // ============================
    // 가입 신청 (승인제)
    // ============================

    /**
     * 승인제 스터디에 가입을 신청합니다.
     *
     * [이 메서드가 StudyService에 있는 이유]
     * 가입 신청은 "사용자"가 하는 행위입니다.
     * 프로젝트의 컨트롤러 기준 서비스 분리 원칙에 따라:
     *   StudyController(사용자 행위) -> StudyService
     *   StudySettingsController(관리자 행위) -> StudySettingsService
     *
     * [처리 흐름]
     * 1. 중복 신청 방지 (이미 PENDING인 신청이 있으면 예외)
     * 2. JoinRequest 생성 및 저장
     * 3. StudyUpdateEvent 발행 -> 관리자+멤버에게 알림
     *
     * @param study   대상 스터디
     * @param account 신청자
     * @return 생성된 JoinRequest
     * @throws IllegalStateException 이미 대기 중인 신청이 있는 경우
     */
    public JoinRequest createJoinRequest(Study study, Account account) {
        if (joinRequestRepository.existsByStudyAndAccountAndStatus(study, account, JoinRequestStatus.PENDING)) {
            throw new IllegalStateException("이미 가입 신청 중입니다.");
        }

        JoinRequest joinRequest = JoinRequest.createRequest(account, study);
        joinRequestRepository.save(joinRequest);

        eventPublisher.publishEvent(new StudyUpdateEvent(study, account.getNickname() + "님이 가입을 신청했습니다."));

        return joinRequest;
    }

    /**
     * 특정 사용자가 해당 스터디에 대기 중인 가입 신청이 있는지 확인합니다.
     * <p>
     * [사용처]
     * StudyController.viewStudy()에서 호출하여 모델에 "hasPendingRequest"로 전달합니다.
     * 뷰(study-info fragment)에서 이 값을 보고 버튼을 분기합니다:
     * - true  -> "신청 중" (비활성 버튼)
     * - false -> "가입 신청" (활성 버튼)
     */
    public boolean hasPendingJoinRequest(Study study, Account account) {
        return joinRequestRepository.existsByStudyAndAccountAndStatus(study, account, JoinRequestStatus.PENDING);
    }

    // ============================
    // 내부 검증 유틸리티
    // ============================

    /**
     * 스터디 존재 여부를 검증합니다.
     * null인 경우 IllegalArgumentException을 발생시킵니다.
     *
     * @param path  요청된 스터디 경로 (에러 메시지용)
     * @param study 조회 결과 (null 가능)
     * @throws IllegalArgumentException 스터디가 존재하지 않는 경우
     */
    private void checkIfExistingStudy(String path, Study study) {
        if (study == null) {
            throw new IllegalArgumentException(path + "에 해당하는 스터디가 없습니다.");
        }
    }

    // ============================
    // 프로필 페이지용 스터디 목록 조회
    // ============================

    /**
     * 특정 사용자가 관리자로 참여하고 있는 활동 중인 스터디 목록을 조회합니다.
     *
     * 프로필 페이지에서 "관리 중인 스터디" 섹션에 사용됩니다.
     * tags, zones가 fetch join되어 뷰에서 지연 로딩 문제 없이 접근 가능합니다.
     *
     * @param account 조회 대상 사용자
     * @return 관리 중인 스터디 목록 (최신 공개 순)
     */
    @Transactional(readOnly = true)
    public List<Study> getStudiesAsManager(Account account) {
        return studyRepository.findByManagersContainingAndClosedOrderByPublishedDateTimeDesc(account, false);
    }

    /**
     * 특정 사용자가 멤버로 참여하고 있는 활동 중인 스터디 목록을 조회합니다.
     *
     * 프로필 페이지에서 "참여 중인 스터디" 섹션에 사용됩니다.
     *
     * @param account 조회 대상 사용자
     * @return 참여 중인 스터디 목록 (최신 공개 순)
     */
    @Transactional(readOnly = true)
    public List<Study> getStudiesAsMember(Account account) {
        return studyRepository.findByMembersContainingAndClosedOrderByPublishedDateTimeDesc(account, false);
    }
}