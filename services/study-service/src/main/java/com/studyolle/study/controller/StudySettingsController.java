package com.studyolle.study.controller;

import com.studyolle.study.client.MetadataFeignClient;
import com.studyolle.study.common.EmailVerifiedGuard;
import com.studyolle.study.dto.request.TagRequest;
import com.studyolle.study.dto.request.UpdateStudyDescriptionRequest;
import com.studyolle.study.dto.request.ZoneRequest;
import com.studyolle.study.dto.response.CommonApiResponse;
import com.studyolle.study.dto.response.JoinRequestResponse;
import com.studyolle.study.dto.response.StudyResponse;
import com.studyolle.study.entity.JoinRequestStatus;
import com.studyolle.study.entity.Study;
import com.studyolle.study.repository.JoinRequestRepository;
import com.studyolle.study.service.StudyService;
import com.studyolle.study.service.StudySettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * 스터디 설정 변경 API 컨트롤러.
 *
 * =============================================
 * 경로 설계 원칙
 * =============================================
 *
 * 모든 엔드포인트는 /api/studies/{path}/settings/** 구조를 따른다.
 * {path} 가 URL 에 포함되어 있으므로, 어떤 스터디의 설정을 변경하는지
 * 요청만 봐도 바로 파악할 수 있다.
 *
 * 클래스 레벨 @RequestMapping("/api/studies/{path}/settings") 를 선언하면
 * 모든 메서드가 이 접두사를 자동으로 상속받는다.
 * 메서드마다 전체 경로를 반복 작성하지 않아도 된다.
 *
 * =============================================
 * 권한 검증 패턴
 * =============================================
 *
 * 이 컨트롤러의 모든 엔드포인트는 관리자만 접근할 수 있다.
 * 모든 메서드의 첫 줄은 아래 두 가지 중 하나이다:
 *
 *   studyService.getStudyToUpdate(accountId, path)
 *   → 내부적으로 study.isManagerOf(accountId) 를 검증. 관리자가 아니면 예외를 던진다.
 *   → tagIds, zoneIds 등 컬렉션이 필요한 경우에 사용.
 *
 *   studyService.getStudyToUpdateStatus(accountId, path)
 *   → 동일한 권한 검증. findStudyOnlyByPath 로 컬렉션 없이 기본 필드만 조회.
 *   → publish/close/recruit/path/title 처럼 컬렉션이 필요 없는 경우에 사용.
 *
 * 이 패턴 덕분에 각 메서드에서 권한 검증을 잊어버리는 실수를 방지한다.
 *
 * =============================================
 * JoinRequestRepository 직접 주입 이유
 * =============================================
 *
 * getJoinRequests() 는 JoinRequest 목록을 조회만 하는 단순 읽기 작업이다.
 * StudyService 를 거치면 불필요한 중간 계층이 추가되므로
 * Controller 가 직접 Repository 를 호출한다.
 * 단, 비즈니스 로직(승인/거절)은 반드시 StudyService 를 통한다.
 */
@RestController
@RequestMapping("/api/studies/{path}/settings")
@RequiredArgsConstructor
public class StudySettingsController {

    private final StudyService studyService;
    private final StudySettingsService studySettingsService;
    private final MetadataFeignClient metadataFeignClient;
    private final JoinRequestRepository joinRequestRepository;

    // ============================
    // 소개 수정
    // ============================

    /**
     * PUT /api/studies/{path}/settings/description
     *
     * 스터디 간략 소개와 상세 소개를 수정한다.
     * 수정 후 최신 상태의 StudyResponse 를 반환하여 클라이언트가 화면을 갱신할 수 있게 한다.
     */
    @PutMapping("/description")
    public ResponseEntity<CommonApiResponse<StudyResponse>> updateDescription(
            @RequestHeader("X-Account-Id") Long accountId,
            @RequestHeader("X-Account-Email-Verified") Boolean emailVerified,
            @PathVariable String path,
            @Valid @RequestBody UpdateStudyDescriptionRequest request) {

        EmailVerifiedGuard.require(emailVerified);

        Study study = studyService.getStudyToUpdate(accountId, path); // 권한 검증 + 조회
        studySettingsService.updateStudyDescription(study, request);
        return ResponseEntity.ok(CommonApiResponse.ok(StudyResponse.from(study)));
    }

    // ============================
    // 배너 이미지
    // ============================

    /**
     * PUT /api/studies/{path}/settings/banner
     *
     * 배너 이미지를 변경한다. image 는 Base64 문자열 또는 이미지 URL 이다.
     */
    @PutMapping("/banner")
    public ResponseEntity<CommonApiResponse<Void>> updateBanner(
            @RequestHeader("X-Account-Id") Long accountId,
            @RequestHeader("X-Account-Email-Verified") Boolean emailVerified,
            @PathVariable String path,
            @RequestBody String image) {

        EmailVerifiedGuard.require(emailVerified);

        Study study = studyService.getStudyToUpdate(accountId, path);
        studySettingsService.updateStudyImage(study, image);
        return ResponseEntity.ok(CommonApiResponse.ok("배너 이미지를 변경했습니다."));
    }

    /**
     * POST /api/studies/{path}/settings/banner/enable
     *
     * 배너 사용을 활성화한다. useBanner 필드를 true 로 변경한다.
     */
    @PostMapping("/banner/enable")
    public ResponseEntity<CommonApiResponse<Void>> enableBanner(
            @RequestHeader("X-Account-Id") Long accountId,
            @RequestHeader("X-Account-Email-Verified") Boolean emailVerified,
            @PathVariable String path) {

        EmailVerifiedGuard.require(emailVerified);

        Study study = studyService.getStudyToUpdate(accountId, path);
        studySettingsService.enableStudyBanner(study);
        return ResponseEntity.ok(CommonApiResponse.ok("배너를 활성화했습니다."));
    }

    /**
     * POST /api/studies/{path}/settings/banner/disable
     *
     * 배너 사용을 비활성화한다. useBanner 필드를 false 로 변경한다.
     */
    @PostMapping("/banner/disable")
    public ResponseEntity<CommonApiResponse<Void>> disableBanner(
            @RequestHeader("X-Account-Id") Long accountId,
            @RequestHeader("X-Account-Email-Verified") Boolean emailVerified,
            @PathVariable String path) {

        EmailVerifiedGuard.require(emailVerified);

        Study study = studyService.getStudyToUpdate(accountId, path);
        studySettingsService.disableStudyBanner(study);
        return ResponseEntity.ok(CommonApiResponse.ok("배너를 비활성화했습니다."));
    }

    // ============================
    // 태그 관리
    // ============================

    /**
     * GET /api/studies/{path}/settings/tags
     *
     * 현재 스터디의 tagIds 와 Tagify 자동완성용 전체 태그 이름 목록을 함께 반환한다.
     *
     * TagSettingsResponse 는 이 컨트롤러 하단에 정의된 record 이다.
     * 이 엔드포인트에서만 사용하는 응답 구조이므로 별도 파일로 분리하지 않았다.
     */
    @GetMapping("/tags")
    public ResponseEntity<CommonApiResponse<TagSettingsResponse>> getTagSettings(
            @RequestHeader("X-Account-Id") Long accountId,
            @RequestHeader("X-Account-Email-Verified") Boolean emailVerified,
            @PathVariable String path) {

        EmailVerifiedGuard.require(emailVerified);

        Study study = studyService.getStudyToUpdate(accountId, path);
        List<String> whitelist = metadataFeignClient.getAllTagTitles("study-service"); // Tagify 자동완성용

        return ResponseEntity.ok(CommonApiResponse.ok(
                new TagSettingsResponse(study.getTagIds(), whitelist)));
    }

    /**
     * POST /api/studies/{path}/settings/tags/add
     *
     * 스터디에 태그를 추가한다.
     * StudySettingsService.addTag() 가 metadata-service 에서 ID 를 조회하여 tagIds 에 추가한다.
     */
    @PostMapping("/tags/add")
    public ResponseEntity<CommonApiResponse<Void>> addTag(
            @RequestHeader("X-Account-Id") Long accountId,
            @RequestHeader("X-Account-Email-Verified") Boolean emailVerified,
            @PathVariable String path,
            @Valid @RequestBody TagRequest request) {

        EmailVerifiedGuard.require(emailVerified);

        Study study = studyService.getStudyToUpdate(accountId, path);
        studySettingsService.addTag(study, request.getTagTitle());
        return ResponseEntity.ok(CommonApiResponse.ok("태그를 추가했습니다."));
    }

    /**
     * DELETE /api/studies/{path}/settings/tags/remove
     *
     * 스터디에서 태그를 제거한다.
     * HTTP DELETE 에 요청 바디를 포함하는 것은 표준에서 권장하지 않지만,
     * 태그 이름(문자열)을 URL 에 넣기 어렵고 일관성을 위해 이 방식을 선택했다.
     */
    @DeleteMapping("/tags/remove")
    public ResponseEntity<CommonApiResponse<Void>> removeTag(
            @RequestHeader("X-Account-Id") Long accountId,
            @RequestHeader("X-Account-Email-Verified") Boolean emailVerified,
            @PathVariable String path,
            @Valid @RequestBody TagRequest request) {

        EmailVerifiedGuard.require(emailVerified);

        Study study = studyService.getStudyToUpdate(accountId, path);
        studySettingsService.removeTag(study, request.getTagTitle());
        return ResponseEntity.ok(CommonApiResponse.ok("태그를 제거했습니다."));
    }

    // ============================
    // 지역 관리
    // ============================

    /**
     * GET /api/studies/{path}/settings/zones
     *
     * 현재 스터디의 zoneIds 와 Tagify 자동완성용 전체 지역 이름 목록을 함께 반환한다.
     */
    @GetMapping("/zones")
    public ResponseEntity<CommonApiResponse<ZoneSettingsResponse>> getZoneSettings(
            @RequestHeader("X-Account-Id") Long accountId,
            @RequestHeader("X-Account-Email-Verified") Boolean emailVerified,
            @PathVariable String path) {

        EmailVerifiedGuard.require(emailVerified);

        Study study = studyService.getStudyToUpdate(accountId, path);
        List<String> whitelist = metadataFeignClient.getAllZoneNames("study-service");

        return ResponseEntity.ok(CommonApiResponse.ok(
                new ZoneSettingsResponse(study.getZoneIds(), whitelist)));
    }

    /**
     * POST /api/studies/{path}/settings/zones/add
     *
     * 스터디에 활동 지역을 추가한다.
     */
    @PostMapping("/zones/add")
    public ResponseEntity<CommonApiResponse<Void>> addZone(
            @RequestHeader("X-Account-Id") Long accountId,
            @RequestHeader("X-Account-Email-Verified") Boolean emailVerified,
            @PathVariable String path,
            @Valid @RequestBody ZoneRequest request) {

        EmailVerifiedGuard.require(emailVerified);

        Study study = studyService.getStudyToUpdate(accountId, path);
        studySettingsService.addZone(study, request);
        return ResponseEntity.ok(CommonApiResponse.ok("지역을 추가했습니다."));
    }

    /**
     * DELETE /api/studies/{path}/settings/zones/remove
     *
     * 스터디에서 활동 지역을 제거한다.
     */
    @DeleteMapping("/zones/remove")
    public ResponseEntity<CommonApiResponse<Void>> removeZone(
            @RequestHeader("X-Account-Id") Long accountId,
            @RequestHeader("X-Account-Email-Verified") Boolean emailVerified,
            @PathVariable String path,
            @Valid @RequestBody ZoneRequest request) {

        EmailVerifiedGuard.require(emailVerified);

        Study study = studyService.getStudyToUpdate(accountId, path);
        studySettingsService.removeZone(study, request);
        return ResponseEntity.ok(CommonApiResponse.ok("지역을 제거했습니다."));
    }

    // ============================
    // 스터디 상태 변경
    // ============================

    /**
     * POST /api/studies/{path}/settings/publish
     *
     * 스터디를 공개한다. 공개 후에는 검색 결과에 노출된다.
     * 비가역적 작업이므로 Study.publish() 내부에서 이미 공개 상태면 예외를 던진다.
     * getStudyToUpdateStatus 를 사용하는 이유: 상태 변경에 tagIds/zoneIds 컬렉션이 불필요하다.
     */
    @PostMapping("/publish")
    public ResponseEntity<CommonApiResponse<Void>> publish(
            @RequestHeader("X-Account-Id") Long accountId,
            @RequestHeader("X-Account-Email-Verified") Boolean emailVerified,
            @PathVariable String path) {

        EmailVerifiedGuard.require(emailVerified);

        Study study = studyService.getStudyToUpdateStatus(accountId, path);
        studySettingsService.publish(study, accountId);
        return ResponseEntity.ok(CommonApiResponse.ok("스터디를 공개했습니다."));
    }

    /**
     * POST /api/studies/{path}/settings/close
     *
     * 스터디를 종료한다. 종료 후에는 모집/가입이 불가능하다.
     */
    @PostMapping("/close")
    public ResponseEntity<CommonApiResponse<Void>> close(
            @RequestHeader("X-Account-Id") Long accountId,
            @RequestHeader("X-Account-Email-Verified") Boolean emailVerified,
            @PathVariable String path) {

        EmailVerifiedGuard.require(emailVerified);

        Study study = studyService.getStudyToUpdateStatus(accountId, path);
        studySettingsService.close(study);
        return ResponseEntity.ok(CommonApiResponse.ok("스터디를 종료했습니다."));
    }

    /**
     * POST /api/studies/{path}/settings/recruit/start
     *
     * 팀원 모집을 시작한다.
     * 너무 잦은 변경을 막기 위해 Study.startRecruit() 에서 1시간 제한을 검사한다.
     */
    @PostMapping("/recruit/start")
    public ResponseEntity<CommonApiResponse<Void>> startRecruit(
            @RequestHeader("X-Account-Id") Long accountId,
            @RequestHeader("X-Account-Email-Verified") Boolean emailVerified,
            @PathVariable String path) {

        EmailVerifiedGuard.require(emailVerified);

        Study study = studyService.getStudyToUpdateStatus(accountId, path);
        studySettingsService.startRecruit(study, accountId);
        return ResponseEntity.ok(CommonApiResponse.ok("모집을 시작했습니다."));
    }

    /**
     * POST /api/studies/{path}/settings/recruit/stop
     *
     * 팀원 모집을 중단한다.
     */
    @PostMapping("/recruit/stop")
    public ResponseEntity<CommonApiResponse<Void>> stopRecruit(
            @RequestHeader("X-Account-Id") Long accountId,
            @RequestHeader("X-Account-Email-Verified") Boolean emailVerified,
            @PathVariable String path) {

        EmailVerifiedGuard.require(emailVerified);

        Study study = studyService.getStudyToUpdateStatus(accountId, path);
        studySettingsService.stopRecruit(study, accountId);
        return ResponseEntity.ok(CommonApiResponse.ok("모집을 중단했습니다."));
    }

    // ============================
    // 경로/제목/가입방식 변경 + 삭제
    // ============================

    /**
     * PUT /api/studies/{path}/settings/path
     *
     * 스터디 경로를 변경한다.
     * trim() 으로 앞뒤 공백을 제거한다. 공백이 포함된 경로는 URL 에서 문제가 된다.
     */
    @PutMapping("/path")
    public ResponseEntity<CommonApiResponse<Void>> updatePath(
            @RequestHeader("X-Account-Id") Long accountId,
            @RequestHeader("X-Account-Email-Verified") Boolean emailVerified,
            @PathVariable String path,
            @RequestBody String newPath) {

        EmailVerifiedGuard.require(emailVerified);

        Study study = studyService.getStudyToUpdateStatus(accountId, path);
        studySettingsService.updateStudyPath(study, newPath.trim());
        return ResponseEntity.ok(CommonApiResponse.ok("경로를 변경했습니다."));
    }

    /**
     * PUT /api/studies/{path}/settings/title
     *
     * 스터디 제목을 변경한다.
     */
    @PutMapping("/title")
    public ResponseEntity<CommonApiResponse<Void>> updateTitle(
            @RequestHeader("X-Account-Id") Long accountId,
            @RequestHeader("X-Account-Email-Verified") Boolean emailVerified,
            @PathVariable String path,
            @RequestBody String newTitle) {

        EmailVerifiedGuard.require(emailVerified);

        Study study = studyService.getStudyToUpdateStatus(accountId, path);
        studySettingsService.updateStudyTitle(study, newTitle.trim());
        return ResponseEntity.ok(CommonApiResponse.ok("제목을 변경했습니다."));
    }

    /**
     * DELETE /api/studies/{path}
     *
     * 스터디를 삭제한다.
     * 공개된 스터디는 삭제할 수 없다. Study.isRemovable() 에서 검사한다.
     * 경로가 /settings 없이 /{path} 인 이유: 설정 URL 에 종속되지 않는 독립적인 삭제 의미를 표현하기 위함이다.
     */
    @DeleteMapping
    public ResponseEntity<CommonApiResponse<Void>> removeStudy(
            @RequestHeader("X-Account-Id") Long accountId,
            @RequestHeader("X-Account-Email-Verified") Boolean emailVerified,
            @PathVariable String path) {

        EmailVerifiedGuard.require(emailVerified);

        Study study = studyService.getStudyToUpdate(accountId, path);
        studyService.remove(study);
        return ResponseEntity.ok(CommonApiResponse.ok("스터디를 삭제했습니다."));
    }

    // ============================
    // 가입 신청 관리 (승인제)
    // ============================

    /**
     * GET /api/studies/{path}/settings/join-requests
     *
     * 대기 중인(PENDING) 가입 신청 목록을 반환한다. 관리자 전용.
     *
     * JoinRequestRepository 를 직접 호출하는 이유:
     * 단순 읽기 조회이므로 Service 를 거치면 불필요한 중간 계층이 추가된다.
     * 단, 데이터를 변경하는 승인/거절은 반드시 StudyService 를 통한다.
     */
    @GetMapping("/join-requests")
    public ResponseEntity<CommonApiResponse<List<JoinRequestResponse>>> getJoinRequests(
            @RequestHeader("X-Account-Id") Long accountId,
            @RequestHeader("X-Account-Email-Verified") Boolean emailVerified,
            @PathVariable String path) {

        EmailVerifiedGuard.require(emailVerified);

        Study study = studyService.getStudyToUpdate(accountId, path); // 관리자 권한 검증
        List<JoinRequestResponse> result = joinRequestRepository
                .findByStudyAndStatusOrderByRequestedAtAsc(study, JoinRequestStatus.PENDING)
                .stream().map(JoinRequestResponse::from).toList();

        return ResponseEntity.ok(CommonApiResponse.ok(result));
    }

    /**
     * POST /api/studies/{path}/settings/join-requests/{requestId}/approve
     *
     * 가입 신청을 승인한다. PENDING → APPROVED 로 변경하고 memberIds 에 추가한다.
     *
     * 경로에 {path} 가 있지만 승인 로직에는 study 자체가 필요 없다.
     * getStudyToUpdate() 는 오직 관리자 권한 검증 목적으로만 호출한다.
     */
    @PostMapping("/join-requests/{requestId}/approve")
    public ResponseEntity<CommonApiResponse<Void>> approveJoinRequest(
            @RequestHeader("X-Account-Id") Long accountId,
            @RequestHeader("X-Account-Email-Verified") Boolean emailVerified,
            @PathVariable String path,
            @PathVariable Long requestId) {

        EmailVerifiedGuard.require(emailVerified);

        studyService.getStudyToUpdate(accountId, path); // 관리자 권한 검증
        studyService.approveJoinRequest(requestId);
        return ResponseEntity.ok(CommonApiResponse.ok("가입 신청을 승인했습니다."));
    }

    /**
     * POST /api/studies/{path}/settings/join-requests/{requestId}/reject
     *
     * 가입 신청을 거절한다. PENDING → REJECTED 로 변경한다.
     */
    @PostMapping("/join-requests/{requestId}/reject")
    public ResponseEntity<CommonApiResponse<Void>> rejectJoinRequest(
            @RequestHeader("X-Account-Id") Long accountId,
            @RequestHeader("X-Account-Email-Verified") Boolean emailVerified,
            @PathVariable String path,
            @PathVariable Long requestId) {

        EmailVerifiedGuard.require(emailVerified);

        studyService.getStudyToUpdate(accountId, path); // 관리자 권한 검증
        studyService.rejectJoinRequest(requestId);
        return ResponseEntity.ok(CommonApiResponse.ok("가입 신청을 거절했습니다."));
    }

    // ============================
    // 이 컨트롤러 전용 응답 DTO
    // ============================

    /**
     * 태그 설정 화면 응답 DTO.
     *
     * Java 16+ 의 record 는 불변 데이터 클래스를 간결하게 선언하는 문법이다.
     * record TagSettingsResponse(Set<Long> tagIds, List<String> whitelist) {} 선언만으로
     * 생성자, getter, equals, hashCode, toString 이 자동 생성된다.
     *
     * 이 컨트롤러에서만 쓰이는 단순 응답 구조라서 별도 파일로 분리하지 않고
     * 내부 클래스로 선언했다.
     */
    record TagSettingsResponse(Set<Long> tagIds, List<String> whitelist) {}

    /**
     * 지역 설정 화면 응답 DTO.
     */
    record ZoneSettingsResponse(Set<Long> zoneIds, List<String> whitelist) {}
}