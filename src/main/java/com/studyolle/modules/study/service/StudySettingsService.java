package com.studyolle.modules.study.service;

import com.studyolle.modules.account.entity.Account;
import com.studyolle.modules.study.dto.StudyDescriptionForm;
import com.studyolle.modules.study.entity.JoinRequest;
import com.studyolle.modules.study.entity.JoinRequestStatus;
import com.studyolle.modules.study.entity.JoinType;
import com.studyolle.modules.study.entity.Study;
import com.studyolle.modules.study.event.StudyCreatedEvent;
import com.studyolle.modules.study.event.StudyUpdateEvent;
import com.studyolle.modules.study.repository.JoinRequestRepository;
import com.studyolle.modules.study.repository.StudyRepository;
import com.studyolle.modules.tag.Tag;
import com.studyolle.modules.zone.Zone;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.studyolle.modules.study.dto.StudyForm.VALID_PATH_PATTERN;

/**
 * StudySettingsService - 스터디 설정 관련 비즈니스 로직 담당
 *
 * =============================================
 * 역할과 책임 범위
 * =============================================
 *
 * 이 서비스는 StudySettingsController가 필요로 하는 모든 비즈니스 로직을 담당합니다.
 * StudyService가 스터디의 "핵심 생명주기"(생성, 조회, 멤버 관리)를 다루는 반면,
 * 이 클래스는 스터디의 "설정과 운영"에 집중합니다.
 *
 * 담당 영역:
 * - 소개/배너 수정 (description, image, banner)
 * - 태그/지역 관리 (tags, zones)
 * - 상태 관리 (publish, close, recruit)
 * - 경로/제목 변경 (path, title)
 * - 스터디 삭제 (remove)
 *
 * =============================================
 * 이벤트 발행 패턴
 * =============================================
 *
 * 스터디 상태가 변경될 때(공개, 종료, 모집, 소개 수정 등) ApplicationEventPublisher를
 * 통해 도메인 이벤트를 발행합니다. 이벤트 리스너(StudyEventListener)가 이를 감지하여
 * 알림 생성, 이메일 발송 등의 후속 작업을 비동기로 처리합니다.
 *
 * - StudyCreatedEvent: 스터디가 최초 공개될 때 발행
 *   -> 관심 태그/지역이 일치하는 사용자들에게 알림
 * - StudyUpdateEvent: 스터디 정보가 변경될 때 발행 (종료, 모집, 소개 수정 등)
 *   -> 해당 스터디의 관리자 + 멤버에게 알림
 *
 * =============================================
 * Dirty Checking 기반 자동 반영
 * =============================================
 *
 * 이 클래스의 대부분의 메서드에서 명시적인 save() 호출이 없습니다.
 * 이는 JPA의 영속성 컨텍스트와 Dirty Checking 메커니즘 덕분입니다.
 *
 * 조건:
 * 1. Study 객체가 영속 상태여야 함 (Repository를 통해 조회된 객체)
 * 2. 해당 메서드가 @Transactional 범위 내에서 실행되어야 함
 *
 * 이 두 조건이 충족되면, 엔티티 필드 변경 시 트랜잭션 커밋 시점에
 * JPA가 자동으로 변경된 필드를 감지하여 UPDATE SQL을 실행합니다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class StudySettingsService {

    private final StudyRepository studyRepository;
    private final ModelMapper modelMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final JoinRequestRepository joinRequestRepository;

    // ============================
    // 스터디 조회 (설정 수정용)
    // ============================

    /**
     * 일반 설정 수정을 위한 스터디 조회.
     * 모든 연관 엔티티(tags, zones, managers, members)를 함께 로딩합니다.
     *
     * @param account 현재 로그인한 사용자 (관리자 권한 확인용)
     * @param path    스터디 URL 경로
     * @return 권한이 검증된 Study 엔티티
     * @throws AccessDeniedException    관리자가 아닌 경우
     * @throws IllegalArgumentException 스터디가 존재하지 않는 경우
     */
    public Study getStudyToUpdate(Account account, String path) {
        Study study = studyRepository.findByPath(path);
        checkIfExistingStudy(path, study);
        checkIfManager(account, study);
        return study;
    }

    /**
     * 태그 수정을 위한 스터디 조회.
     * tags와 managers만 fetch join하여 최소한의 데이터만 로딩합니다.
     *
     * 태그 설정 화면에서는 zones, members 정보가 불필요하므로,
     * findAccountWithTagsByPath()를 사용하여 성능을 최적화합니다.
     */
    public Study getStudyToUpdateTag(Account account, String path) {
        Study study = studyRepository.findAccountWithTagsByPath(path);
        checkIfExistingStudy(path, study);
        checkIfManager(account, study);
        return study;
    }

    /**
     * 지역(Zone) 수정을 위한 스터디 조회.
     * zones와 managers만 fetch join하여 최소한의 데이터만 로딩합니다.
     */
    public Study getStudyToUpdateZone(Account account, String path) {
        Study study = studyRepository.findAccountWithZonesByPath(path);
        checkIfExistingStudy(path, study);
        checkIfManager(account, study);
        return study;
    }

    /**
     * 스터디 상태 변경(공개/종료/모집/경로/제목/삭제)을 위한 스터디 조회.
     * managers만 fetch join하여 권한 확인에 필요한 최소 데이터만 로딩합니다.
     *
     * 상태 변경 작업은 Study 엔티티 자체의 상태 필드만 수정하므로,
     * tags, zones, members를 함께 로딩할 필요가 없습니다.
     *
     * @param account 현재 로그인한 사용자
     * @param path    스터디 URL 경로
     * @return managers가 로딩된 Study 엔티티
     */
    public Study getStudyToUpdateStatus(Account account, String path) {
        Study study = studyRepository.findStudyWithManagersByPath(path);
        checkIfExistingStudy(path, study);
        checkIfManager(account, study);
        return study;
    }

    // ============================
    // 소개 / 배너 수정
    // ============================

    /**
     * 스터디 소개(shortDescription, fullDescription) 업데이트.
     *
     * ModelMapper를 사용하여 DTO의 필드 값을 영속 상태의 Study 엔티티에 복사합니다.
     * 내부적으로 study.setShortDescription(), study.setFullDescription() 등이 호출되며,
     * 이 변경은 JPA Dirty Checking에 의해 트랜잭션 커밋 시 자동으로 DB에 반영됩니다.
     *
     * 수정 완료 후 StudyUpdateEvent를 발행하여 관련 사용자에게 알림을 전송합니다.
     *
     * @param study                영속 상태의 Study 엔티티
     * @param studyDescriptionForm 사용자가 입력한 소개 수정 폼 데이터
     */
    public void updateStudyDescription(Study study, StudyDescriptionForm studyDescriptionForm) {
        modelMapper.map(studyDescriptionForm, study);
        eventPublisher.publishEvent(new StudyUpdateEvent(study, "스터디 소개를 수정했습니다."));
    }

    /**
     * 스터디 배너 이미지 업데이트.
     * Base64 인코딩된 이미지 데이터를 Study 엔티티의 image 필드에 저장합니다.
     */
    public void updateStudyImage(Study study, String image) {
        study.setImage(image);
    }

    /**
     * 배너 이미지 표시를 활성화합니다.
     * useBanner 플래그를 true로 설정하면, 뷰에서 배너 이미지를 렌더링합니다.
     */
    public void enableStudyBanner(Study study) {
        study.setUseBanner(true);
    }

    /**
     * 배너 이미지 표시를 비활성화합니다.
     */
    public void disableStudyBanner(Study study) {
        study.setUseBanner(false);
    }

    // ============================
    // 태그 관리
    // ============================

    /**
     * 스터디에 태그를 추가합니다.
     *
     * Study.tags는 @ManyToMany 관계이므로, Set에 add()하면
     * JPA가 중간 테이블(study_tags)에 INSERT를 자동 실행합니다.
     *
     * @param study 영속 상태의 Study (tags가 로딩된 상태)
     * @param tag   추가할 Tag 엔티티
     */
    public void addTag(Study study, Tag tag) {
        study.getTags().add(tag);
    }

    /**
     * 스터디에서 태그를 제거합니다.
     */
    public void removeTag(Study study, Tag tag) {
        study.getTags().remove(tag);
    }

    // ============================
    // 지역(Zone) 관리
    // ============================

    /**
     * 스터디에 활동 지역을 추가합니다.
     *
     * @param study 영속 상태의 Study (zones가 로딩된 상태)
     * @param zone  추가할 Zone 엔티티
     */
    public void addZone(Study study, Zone zone) {
        study.getZones().add(zone);
    }

    /**
     * 스터디에서 활동 지역을 제거합니다.
     */
    public void removeZone(Study study, Zone zone) {
        study.getZones().remove(zone);
    }

    // ============================
    // 스터디 상태 관리
    // ============================

    /**
     * 스터디를 공개 처리하고 StudyCreatedEvent를 발행합니다.
     *
     * 공개는 스터디의 최초 외부 노출을 의미하며, 이 시점에서
     * 관심 태그/지역이 일치하는 사용자들에게 알림이 전송됩니다.
     *
     * StudyCreatedEvent vs StudyUpdateEvent 구분:
     * - StudyCreatedEvent: 관심사 기반으로 "새로운 사용자"에게 알림 (잠재적 멤버 대상)
     * - StudyUpdateEvent: 이미 소속된 "기존 관리자+멤버"에게 알림
     *
     * @param study 공개할 Study 엔티티
     * @throws RuntimeException 이미 공개되었거나 종료된 스터디인 경우
     */
    public void publish(Study study) {
        study.publish();
        eventPublisher.publishEvent(new StudyCreatedEvent(study));
    }

    /**
     * 스터디를 종료 처리하고 StudyUpdateEvent를 발행합니다.
     *
     * 종료된 스터디는 더 이상 모집이 불가능하며,
     * 기존 관리자와 멤버에게 종료 알림이 전송됩니다.
     *
     * @param study 종료할 Study 엔티티
     * @throws RuntimeException 공개되지 않았거나 이미 종료된 스터디인 경우
     */
    public void close(Study study) {
        study.close();
        eventPublisher.publishEvent(new StudyUpdateEvent(study, "스터디를 종료했습니다."));
    }

    /**
     * 팀원 모집을 시작합니다.
     *
     * 모집 상태 변경에는 1시간 쿨다운이 적용됩니다.
     * canUpdateRecruiting() 검증은 StudySettingsController에서 먼저 수행하지만,
     * Study 도메인 내부에서도 이중으로 검증합니다 (방어적 프로그래밍).
     *
     * @param study 모집을 시작할 Study 엔티티
     * @throws RuntimeException 쿨다운 기간 내이거나 공개되지 않은 스터디인 경우
     */
    public void startRecruit(Study study) {
        study.startRecruit();
        eventPublisher.publishEvent(new StudyUpdateEvent(study, "팀원 모집을 시작합니다."));
    }

    /**
     * 팀원 모집을 중단합니다.
     *
     * @param study 모집을 중단할 Study 엔티티
     * @throws RuntimeException 쿨다운 기간 내이거나 공개되지 않은 스터디인 경우
     */
    public void stopRecruit(Study study) {
        study.stopRecruit();
        eventPublisher.publishEvent(new StudyUpdateEvent(study, "팀원 모집을 중단했습니다."));
    }

    // ============================
    // 가입 방식 설정
    // ============================

    /**
     * 스터디의 가입 방식을 변경합니다.
     *
     * 단순 setter 호출이지만, 컨트롤러가 엔티티를 직접 수정하지 않는 프로젝트 원칙에 따라
     * 서비스를 경유합니다. enableStudyBanner(), disableStudyBanner()와 같은 패턴입니다.
     *
     * [사용처]
     * StudySettingsController.updateJoinType() -> 이 메서드 호출
     */
    public void updateJoinType(Study study, JoinType joinType) {
        study.setJoinType(joinType);
    }

    // ============================
    // 가입 신청 관리
    // ============================

    /**
     * 특정 스터디의 대기 중인 가입 신청 목록을 조회합니다.
     * <p>
     * [사용처]
     * StudySettingsController.joinRequestsForm() -> 관리자용 신청 목록 페이지 렌더링
     * <p>
     * [이 메서드가 StudySettingsService에 있는 이유]
     * 신청 목록 조회는 "관리자"가 설정 페이지에서 하는 행위이므로
     * StudySettingsController -> StudySettingsService 경로를 따릅니다.
     */
    public List<JoinRequest> getPendingJoinRequests(Study study) {
        return joinRequestRepository.findByStudyAndStatusOrderByRequestedAtAsc(study, JoinRequestStatus.PENDING);
    }

    /**
     * 가입 신청을 승인합니다.
     *
     * [처리 흐름]
     * 1. JoinRequest 조회 (없으면 예외)
     * 2. 이미 처리된 신청인지 확인 (중복 처리 방지)
     * 3. request.approve() -> 엔티티가 스스로 상태 전이 (PENDING -> APPROVED)
     * 4. study.addMember() -> 기존 멤버 등록 로직 재활용!
     * 5. 이벤트 발행 -> 알림
     *
     * [핵심 포인트]
     * 승인 시 멤버 등록은 Study.addMember()를 그대로 재활용합니다.
     * 새로운 멤버 등록 로직을 만들 필요가 없습니다.
     *
     * @param requestId 가입 신청 ID
     * @param study     대상 스터디 (members가 로딩된 상태여야 함)
     * @throws IllegalArgumentException 존재하지 않는 신청
     * @throws IllegalStateException    이미 처리된 신청
     */
    public void approveJoinRequest(Long requestId, Study study) {
        JoinRequest request = joinRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 가입 신청입니다."));

        if (!request.isPending()) {
            throw new IllegalStateException("이미 처리된 신청입니다.");
        }

        request.approve();
        study.addMember(request.getAccount());

        eventPublisher.publishEvent(new StudyUpdateEvent(study, request.getAccount().getNickname() + "님의 가입이 승인되었습니다."));
    }

    /**
     * 가입 신청을 거절합니다.
     *
     * 승인과의 차이: study.addMember()가 호출되지 않습니다.
     * 상태만 REJECTED로 변경하고, 알림을 발행합니다.
     *
     * @param requestId 가입 신청 ID
     * @param study     대상 스터디
     */
    public void rejectJoinRequest(Long requestId, Study study) {
        JoinRequest request = joinRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 가입 신청입니다."));

        if (!request.isPending()) {
            throw new IllegalStateException("이미 처리된 신청입니다.");
        }

        request.reject();

        eventPublisher.publishEvent(new StudyUpdateEvent(study, request.getAccount().getNickname() + "님의 가입이 거절되었습니다."));
    }

    // ============================
    // 경로 / 제목 변경
    // ============================

    /**
     * 새로운 경로(path)의 유효성을 검사합니다.
     *
     * 검증 기준:
     * 1. VALID_PATH_PATTERN 정규식 일치 여부 (한글/영문/숫자/하이픈/언더스코어, 2~20자)
     * 2. 다른 스터디에서 이미 사용 중인 path가 아닌지 확인
     *
     * @param newPath 변경하려는 새 경로
     * @return 유효하면 true, 아니면 false
     */
    public boolean isValidPath(String newPath) {
        if (!newPath.matches(VALID_PATH_PATTERN)) {
            return false;
        }
        return !studyRepository.existsByPath(newPath);
    }

    /**
     * 스터디 경로를 변경합니다.
     * 유효성 검증은 컨트롤러에서 isValidPath()로 사전 수행합니다.
     */
    public void updateStudyPath(Study study, String newPath) {
        study.setPath(newPath);
    }

    /**
     * 새로운 제목의 유효성을 검사합니다.
     *
     * @param newTitle 변경하려는 새 제목
     * @return 50자 이하이면 true
     */
    public boolean isValidTitle(String newTitle) {
        return newTitle.length() <= 50;
    }

    /**
     * 스터디 제목을 변경합니다.
     */
    public void updateStudyTitle(Study study, String newTitle) {
        study.setTitle(newTitle);
    }

    // ============================
    // 스터디 삭제
    // ============================

    /**
     * 스터디를 삭제합니다.
     *
     * 삭제 조건: 스터디가 아직 공개(publish)되지 않은 상태여야 합니다.
     * 이미 공개된 스터디는 참여자가 존재할 수 있으므로 삭제가 불가능합니다.
     *
     * @param study 삭제할 Study 엔티티
     * @throws IllegalArgumentException 공개된 스터디를 삭제하려는 경우
     */
    public void remove(Study study) {
        if (study.isRemovable()) {
            studyRepository.delete(study);
        } else {
            throw new IllegalArgumentException("스터디를 삭제할 수 없습니다.");
        }
    }

    // ============================
    // 내부 검증 유틸리티
    // ============================

    /**
     * 현재 사용자가 해당 스터디의 관리자인지 확인합니다.
     *
     * @param account 현재 로그인한 사용자
     * @param study   검증 대상 스터디
     * @throws AccessDeniedException 관리자가 아닌 경우
     */
    private void checkIfManager(Account account, Study study) {
        if (!study.isManagerOf(account)) {
            throw new AccessDeniedException("해당 기능을 사용할 수 없습니다.");
        }
    }

    /**
     * 스터디 존재 여부를 검증합니다.
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
}