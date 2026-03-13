package com.studyolle.study.controller;

import com.studyolle.study.client.MetadataFeignClient;
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
 * StudySettingsController — 스터디 설정 변경 API
 *
 * =============================================
 * [모노리틱 참조: StudySettingsController.java]
 * =============================================
 *
 * [핵심 변경: TagService, ZoneService → MetadataFeignClient]
 * 모노리틱에서 Tagify 자동완성 whitelist 를 위해 tagRepository.findAll(),
 * zoneRepository.findAll() 을 직접 호출했으나,
 * MSA 에서는 MetadataFeignClient 를 통해 metadata-service 에서 조회한다.
 *
 * [가입 신청 관리]
 * 모노리틱의 StudySettingsController 에 포함되어 있던 가입 신청 승인/거절 로직을
 * 이 컨트롤러에서 담당한다.
 *
 * [경로 패턴]
 * /api/studies/{path}/settings/**
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
     * [모노리틱 참조: StudySettingsController.updateStudyInfo(account, path, form)]
     */
    @PutMapping("/description")
    public ResponseEntity<CommonApiResponse<StudyResponse>> updateDescription(
            @RequestHeader("X-Account-Id") Long accountId,
            @PathVariable String path,
            @Valid @RequestBody UpdateStudyDescriptionRequest request) {

        Study study = studyService.getStudyToUpdate(accountId, path);
        studySettingsService.updateStudyDescription(study, request);
        return ResponseEntity.ok(CommonApiResponse.ok(StudyResponse.from(study)));
    }

    // ============================
    // 배너 이미지
    // ============================

    /**
     * PUT /api/studies/{path}/settings/banner
     *
     * [모노리틱 참조: StudySettingsController.studyImageSubmit(account, path, image)]
     */
    @PutMapping("/banner")
    public ResponseEntity<CommonApiResponse<Void>> updateBanner(
            @RequestHeader("X-Account-Id") Long accountId,
            @PathVariable String path,
            @RequestBody String image) {

        Study study = studyService.getStudyToUpdate(accountId, path);
        studySettingsService.updateStudyImage(study, image);
        return ResponseEntity.ok(CommonApiResponse.ok("배너 이미지를 변경했습니다."));
    }

    /**
     * POST /api/studies/{path}/settings/banner/enable
     *
     * [모노리틱 참조: StudySettingsController.enableStudyBanner(account, path)]
     */
    @PostMapping("/banner/enable")
    public ResponseEntity<CommonApiResponse<Void>> enableBanner(
            @RequestHeader("X-Account-Id") Long accountId,
            @PathVariable String path) {

        Study study = studyService.getStudyToUpdate(accountId, path);
        studySettingsService.enableStudyBanner(study);
        return ResponseEntity.ok(CommonApiResponse.ok("배너를 활성화했습니다."));
    }

    /**
     * POST /api/studies/{path}/settings/banner/disable
     *
     * [모노리틱 참조: StudySettingsController.disableStudyBanner(account, path)]
     */
    @PostMapping("/banner/disable")
    public ResponseEntity<CommonApiResponse<Void>> disableBanner(
            @RequestHeader("X-Account-Id") Long accountId,
            @PathVariable String path) {

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
     * 현재 스터디의 태그 ID 목록 + 전체 태그 whitelist(Tagify 자동완성용) 반환.
     *
     * [모노리틱 참조: StudySettingsController.studyTagsForm(account, path, model)]
     * 모노리틱에서는 tagRepository.findAll() 로 whitelist 를 만들었으나,
     * MSA 에서는 MetadataFeignClient 로 조회한다.
     */
    @GetMapping("/tags")
    public ResponseEntity<CommonApiResponse<TagSettingsResponse>> getTagSettings(
            @RequestHeader("X-Account-Id") Long accountId,
            @PathVariable String path) {

        Study study = studyService.getStudyToUpdate(accountId, path);
        List<String> whitelist = metadataFeignClient.getAllTagTitles("study-service");

        return ResponseEntity.ok(CommonApiResponse.ok(
                new TagSettingsResponse(study.getTagIds(), whitelist)));
    }

    /**
     * POST /api/studies/{path}/settings/tags/add
     *
     * [모노리틱 참조: StudySettingsController.addTag(account, path, tagForm)]
     */
    @PostMapping("/tags/add")
    public ResponseEntity<CommonApiResponse<Void>> addTag(
            @RequestHeader("X-Account-Id") Long accountId,
            @PathVariable String path,
            @Valid @RequestBody TagRequest request) {

        Study study = studyService.getStudyToUpdate(accountId, path);
        studySettingsService.addTag(study, request.getTagTitle());
        return ResponseEntity.ok(CommonApiResponse.ok("태그를 추가했습니다."));
    }

    /**
     * DELETE /api/studies/{path}/settings/tags/remove
     *
     * [모노리틱 참조: StudySettingsController.removeTag(account, path, tagForm)]
     */
    @DeleteMapping("/tags/remove")
    public ResponseEntity<CommonApiResponse<Void>> removeTag(
            @RequestHeader("X-Account-Id") Long accountId,
            @PathVariable String path,
            @Valid @RequestBody TagRequest request) {

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
     * 현재 스터디의 지역 ID 목록 + 전체 지역 whitelist 반환.
     *
     * [모노리틱 참조: StudySettingsController.studyZonesForm(account, path, model)]
     */
    @GetMapping("/zones")
    public ResponseEntity<CommonApiResponse<ZoneSettingsResponse>> getZoneSettings(
            @RequestHeader("X-Account-Id") Long accountId,
            @PathVariable String path) {

        Study study = studyService.getStudyToUpdate(accountId, path);
        List<String> whitelist = metadataFeignClient.getAllZoneNames("study-service");

        return ResponseEntity.ok(CommonApiResponse.ok(
                new ZoneSettingsResponse(study.getZoneIds(), whitelist)));
    }

    /**
     * POST /api/studies/{path}/settings/zones/add
     *
     * [모노리틱 참조: StudySettingsController.addZone(account, path, zoneForm)]
     */
    @PostMapping("/zones/add")
    public ResponseEntity<CommonApiResponse<Void>> addZone(
            @RequestHeader("X-Account-Id") Long accountId,
            @PathVariable String path,
            @Valid @RequestBody ZoneRequest request) {

        Study study = studyService.getStudyToUpdate(accountId, path);
        studySettingsService.addZone(study, request);
        return ResponseEntity.ok(CommonApiResponse.ok("지역을 추가했습니다."));
    }

    /**
     * DELETE /api/studies/{path}/settings/zones/remove
     *
     * [모노리틱 참조: StudySettingsController.removeZone(account, path, zoneForm)]
     */
    @DeleteMapping("/zones/remove")
    public ResponseEntity<CommonApiResponse<Void>> removeZone(
            @RequestHeader("X-Account-Id") Long accountId,
            @PathVariable String path,
            @Valid @RequestBody ZoneRequest request) {

        Study study = studyService.getStudyToUpdate(accountId, path);
        studySettingsService.removeZone(study, request);
        return ResponseEntity.ok(CommonApiResponse.ok("지역을 제거했습니다."));
    }

    // ============================
    // 스터디 상태 변경
    // ============================

    /** POST /api/studies/{path}/settings/publish */
    @PostMapping("/publish")
    public ResponseEntity<CommonApiResponse<Void>> publish(
            @RequestHeader("X-Account-Id") Long accountId,
            @PathVariable String path) {
        Study study = studyService.getStudyToUpdateStatus(accountId, path);
        studySettingsService.publish(study);
        return ResponseEntity.ok(CommonApiResponse.ok("스터디를 공개했습니다."));
    }

    /** POST /api/studies/{path}/settings/close */
    @PostMapping("/close")
    public ResponseEntity<CommonApiResponse<Void>> close(
            @RequestHeader("X-Account-Id") Long accountId,
            @PathVariable String path) {
        Study study = studyService.getStudyToUpdateStatus(accountId, path);
        studySettingsService.close(study);
        return ResponseEntity.ok(CommonApiResponse.ok("스터디를 종료했습니다."));
    }

    /** POST /api/studies/{path}/settings/recruit/start */
    @PostMapping("/recruit/start")
    public ResponseEntity<CommonApiResponse<Void>> startRecruit(
            @RequestHeader("X-Account-Id") Long accountId,
            @PathVariable String path) {
        Study study = studyService.getStudyToUpdateStatus(accountId, path);
        studySettingsService.startRecruit(study);
        return ResponseEntity.ok(CommonApiResponse.ok("모집을 시작했습니다."));
    }

    /** POST /api/studies/{path}/settings/recruit/stop */
    @PostMapping("/recruit/stop")
    public ResponseEntity<CommonApiResponse<Void>> stopRecruit(
            @RequestHeader("X-Account-Id") Long accountId,
            @PathVariable String path) {
        Study study = studyService.getStudyToUpdateStatus(accountId, path);
        studySettingsService.stopRecruit(study);
        return ResponseEntity.ok(CommonApiResponse.ok("모집을 중단했습니다."));
    }

    // ============================
    // 경로/제목 변경 + 삭제
    // ============================

    /** PUT /api/studies/{path}/settings/path */
    @PutMapping("/path")
    public ResponseEntity<CommonApiResponse<Void>> updatePath(
            @RequestHeader("X-Account-Id") Long accountId,
            @PathVariable String path,
            @RequestBody String newPath) {
        Study study = studyService.getStudyToUpdateStatus(accountId, path);
        studySettingsService.updateStudyPath(study, newPath.trim());
        return ResponseEntity.ok(CommonApiResponse.ok("경로를 변경했습니다."));
    }

    /** PUT /api/studies/{path}/settings/title */
    @PutMapping("/title")
    public ResponseEntity<CommonApiResponse<Void>> updateTitle(
            @RequestHeader("X-Account-Id") Long accountId,
            @PathVariable String path,
            @RequestBody String newTitle) {
        Study study = studyService.getStudyToUpdateStatus(accountId, path);
        studySettingsService.updateStudyTitle(study, newTitle.trim());
        return ResponseEntity.ok(CommonApiResponse.ok("제목을 변경했습니다."));
    }

    /** DELETE /api/studies/{path} */
    @DeleteMapping
    public ResponseEntity<CommonApiResponse<Void>> removeStudy(
            @RequestHeader("X-Account-Id") Long accountId,
            @PathVariable String path) {
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
     * [모노리틱 참조: StudySettingsController.joinRequestsForm(account, path, model)]
     * 대기 중인(PENDING) 가입 신청 목록을 반환한다.
     */
    @GetMapping("/join-requests")
    public ResponseEntity<CommonApiResponse<List<JoinRequestResponse>>> getJoinRequests(
            @RequestHeader("X-Account-Id") Long accountId,
            @PathVariable String path) {

        Study study = studyService.getStudyToUpdate(accountId, path);
        List<JoinRequestResponse> result = joinRequestRepository
                .findByStudyAndStatusOrderByRequestedAtAsc(study, JoinRequestStatus.PENDING)
                .stream().map(JoinRequestResponse::from).toList();

        return ResponseEntity.ok(CommonApiResponse.ok(result));
    }

    /** POST /api/studies/{path}/settings/join-requests/{requestId}/approve */
    @PostMapping("/join-requests/{requestId}/approve")
    public ResponseEntity<CommonApiResponse<Void>> approveJoinRequest(
            @RequestHeader("X-Account-Id") Long accountId,
            @PathVariable String path,
            @PathVariable Long requestId) {

        studyService.getStudyToUpdate(accountId, path); // 관리자 권한 검증
        studyService.approveJoinRequest(requestId);
        return ResponseEntity.ok(CommonApiResponse.ok("가입 신청을 승인했습니다."));
    }

    /** POST /api/studies/{path}/settings/join-requests/{requestId}/reject */
    @PostMapping("/join-requests/{requestId}/reject")
    public ResponseEntity<CommonApiResponse<Void>> rejectJoinRequest(
            @RequestHeader("X-Account-Id") Long accountId,
            @PathVariable String path,
            @PathVariable Long requestId) {

        studyService.getStudyToUpdate(accountId, path); // 관리자 권한 검증
        studyService.rejectJoinRequest(requestId);
        return ResponseEntity.ok(CommonApiResponse.ok("가입 신청을 거절했습니다."));
    }

    // ============================
    // 응답 전용 내부 DTO (이 컨트롤러에서만 사용)
    // ============================

    record TagSettingsResponse(Set<Long> tagIds, List<String> whitelist) {}
    record ZoneSettingsResponse(Set<Long> zoneIds, List<String> whitelist) {}
}