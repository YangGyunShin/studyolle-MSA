package com.studyolle.modules.study.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyolle.modules.account.entity.Account;
import com.studyolle.modules.study.entity.JoinType;
import com.studyolle.modules.study.entity.Study;
import com.studyolle.modules.study.dto.StudyDescriptionForm;
import com.studyolle.modules.study.service.StudySettingsService;
import com.studyolle.modules.tag.Tag;
import com.studyolle.modules.tag.TagForm;
import com.studyolle.modules.tag.TagService;
import com.studyolle.modules.zone.Zone;
import com.studyolle.modules.zone.ZoneForm;
import com.studyolle.modules.zone.ZoneService;
import com.studyolle.modules.study.entity.JoinRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.stream.Collectors;

/**
 * StudySettingsController - 스터디 설정 전반을 담당하는 컨트롤러
 *
 * =============================================
 * 담당 기능
 * =============================================
 *
 * 1. 소개/배너 수정 (GET/POST /description, /banner)
 * 2. 태그 설정 (GET/POST /tags/add, /tags/remove)
 * 3. 지역 설정 (GET/POST /zones/add, /zones/remove)
 * 4. 스터디 상태 관리 (공개/종료/모집 시작/중단)
 * 5. 경로/제목 변경, 스터디 삭제
 *
 * =============================================
 * 계층 구조 (Controller -> Service -> Repository)
 * =============================================
 *
 * - StudySettingsService: 스터디 설정 관련 비즈니스 로직 (조회/수정/상태관리/삭제)
 * - TagService: Tag 조회/생성 (findOrCreateNew, findByTitle, getAllTagTitles)
 * - ZoneService: Zone 조회 (findByCityAndProvince, getAllZoneNames)
 *
 * 이 컨트롤러는 Repository에 직접 접근하지 않으며,
 * 모든 데이터 접근은 위 Service 계층을 통해 수행됩니다.
 *
 * =============================================
 * 서비스 분리 변경 사항
 * =============================================
 *
 * 기존에는 StudyService 하나가 모든 비즈니스 로직을 담당했으나,
 * account 모듈의 분리 패턴(AccountService / SettingsService)에 따라
 * StudySettingsService로 분리되었습니다.
 *
 * 이 컨트롤러에서의 변경:
 * [기존] private final StudyService studyService;
 * [수정] private final StudySettingsService studySettingsService;
 *
 * =============================================
 * @CurrentUser 어노테이션 필수 사항
 * =============================================
 *
 * 이 컨트롤러의 모든 핸들러 메서드에서 Account 파라미터에는 @CurrentUser가 필요합니다.
 * @CurrentUser는 SecurityContext에서 인증된 사용자의 Account 객체를 추출하여 주입합니다.
 *
 * @CurrentUser 없이 Account를 파라미터로 선언하면 Spring은 이것을
 * HTTP 요청 파라미터에서 바인딩하려고 시도하므로, 빈 Account 객체가 생성되거나
 * id가 null인 상태로 주입되어 예상치 못한 오류가 발생합니다.
 */
@Controller
@RequestMapping("/study/{path}/settings")
@RequiredArgsConstructor
public class StudySettingsController {

    private final StudySettingsService studySettingsService;  // 스터디 설정 비즈니스 로직
    private final ModelMapper modelMapper;                     // DTO <-> Entity 변환 자동화
    private final ObjectMapper objectMapper;                   // JSON 직렬화/역직렬화
    private final TagService tagService;                       // 태그 조회/생성 (계층 원칙 유지)
    private final ZoneService zoneService;                     // 지역 조회 (계층 원칙 유지)

    // ============================
    // 공통 헬퍼 메서드
    // ============================

    /**
     * 뷰에서 공통으로 필요한 study와 account 정보를 모델에 추가합니다.
     * 여러 핸들러 메서드에서 반복적으로 사용되는 코드를 추출한 유틸리티 메서드입니다.
     */
    private static void addCommonAttributes(Model model, Account account, Study study) {
        model.addAttribute(study);
        model.addAttribute(account);
    }

    /**
     * 설정 페이지로의 리다이렉트 URL을 생성합니다.
     * Study.getEncodedPath()를 사용하여 한글 경로도 안전하게 처리합니다.
     *
     * @param study 대상 스터디
     * @param path  설정 하위 경로 (e.g., "description", "banner", "study")
     * @return 리다이렉트 URL 문자열
     */
    private static String redirectTo(Study study, String path) {
        return "redirect:/study/" + study.getEncodedPath() + "/settings/" + path;
    }

    // ============================
    // 1~2. 소개 수정 (Description)
    // ============================

    /**
     * [GET] 스터디 소개 설정 화면을 렌더링합니다.
     *
     * 기존 스터디 정보를 StudyDescriptionForm으로 변환하여 폼에 초기값을 채웁니다.
     * ModelMapper가 Study -> StudyDescriptionForm으로 필드명 기반 자동 매핑을 수행합니다.
     */
    @GetMapping("/description")
    public String viewStudySettings(Account account,
                                    @PathVariable String path,
                                    Model model) {
        Study study = studySettingsService.getStudyToUpdate(account, path);
        addCommonAttributes(model, account, study);
        model.addAttribute(modelMapper.map(study, StudyDescriptionForm.class));
        return "study/settings/description";
    }

    /**
     * [POST] 스터디 소개 수정을 처리합니다.
     *
     * 유효성 검증(@Valid) 실패 시 폼을 다시 렌더링하고,
     * 성공 시 DB에 저장 후 flash 메시지와 함께 리다이렉트합니다.
     */
    @PostMapping("/description")
    public String updateStudyInfo(Account account,
                                  @PathVariable String path,
                                  @Valid StudyDescriptionForm studyDescriptionForm,
                                  Errors errors,
                                  Model model,
                                  RedirectAttributes redirectAttributes) {
        Study study = studySettingsService.getStudyToUpdate(account, path);

        if (errors.hasErrors()) {
            addCommonAttributes(model, account, study);
            return "study/settings/description";
        }

        studySettingsService.updateStudyDescription(study, studyDescriptionForm);
        redirectAttributes.addFlashAttribute("message", "스터디 소개를 수정했습니다.");
        return redirectTo(study, "description");
    }

    // ============================
    // 3~6. 배너 설정 (Banner)
    // ============================

    /**
     * [GET] 배너 설정 화면을 렌더링합니다.
     */
    @GetMapping("/banner")
    public String studyImageForm(Account account,
                                 @PathVariable String path,
                                 Model model) {
        Study study = studySettingsService.getStudyToUpdate(account, path);
        addCommonAttributes(model, account, study);
        return "study/settings/banner";
    }

    /**
     * [POST] 배너 이미지 업로드를 처리합니다.
     * 클라이언트에서 Base64로 인코딩된 이미지 데이터를 받아 저장합니다.
     */
    @PostMapping("/banner")
    public String studyImageSubmit(Account account,
                                   @PathVariable String path,
                                   String image,
                                   RedirectAttributes redirectAttributes) {
        Study study = studySettingsService.getStudyToUpdate(account, path);
        studySettingsService.updateStudyImage(study, image);
        redirectAttributes.addFlashAttribute("message", "스터디 이미지를 수정했습니다.");
        return redirectTo(study, "banner");
    }

    /**
     * [POST] 배너 이미지 표시를 활성화합니다.
     */
    @PostMapping("/banner/enable")
    public String enableStudyBanner(Account account,
                                    @PathVariable String path) {
        Study study = studySettingsService.getStudyToUpdate(account, path);
        studySettingsService.enableStudyBanner(study);
        return redirectTo(study, "banner");
    }

    /**
     * [POST] 배너 이미지 표시를 비활성화합니다.
     */
    @PostMapping("/banner/disable")
    public String disableStudyBanner(Account account,
                                     @PathVariable String path) {
        Study study = studySettingsService.getStudyToUpdate(account, path);
        studySettingsService.disableStudyBanner(study);
        return redirectTo(study, "banner");
    }

    // ============================
    // 7~9. 태그 설정 (Tags)
    // ============================

    /**
     * [GET] 태그 설정 화면을 렌더링합니다.
     *
     * Tagify 라이브러리와 연동하기 위해 두 가지 데이터를 준비합니다:
     * - tags: 현재 스터디에 등록된 태그 목록 (Tagify 초기값)
     * - whitelist: 전체 태그 목록을 JSON으로 직렬화 (Tagify 자동완성 후보)
     *
     * TagService.getAllTagTitles()를 통해 Tag -> String 변환을 서비스 계층에서 처리합니다.
     */
    @GetMapping("/tags")
    public String studyTagsForm(Account account,
                                @PathVariable String path, Model model) throws JsonProcessingException {
        Study study = studySettingsService.getStudyToUpdate(account, path);
        addCommonAttributes(model, account, study);

        model.addAttribute("tags",
                study.getTags()
                        .stream()
                        .map(Tag::getTitle)
                        .collect(Collectors.toList()));

        List<String> allTagTitles = tagService.getAllTagTitles();
        model.addAttribute("whitelist", objectMapper.writeValueAsString(allTagTitles));

        return "study/settings/tags";
    }

    /**
     * [POST] 스터디에 태그를 추가합니다. (AJAX 요청)
     *
     * TagService.findOrCreateNew()는 기존 태그가 있으면 반환하고,
     * 없으면 새로 생성하여 반환합니다 (Get-or-Create 패턴).
     *
     * @param tagForm JSON 본문에서 바인딩된 태그 정보 (tagTitle 필드)
     * @return 200 OK 또는 에러 응답
     */
    @PostMapping("/tags/add")
    @ResponseBody
    public ResponseEntity addTag(Account account,
                                 @PathVariable String path,
                                 @RequestBody TagForm tagForm) {
        Study study = studySettingsService.getStudyToUpdateTag(account, path);
        Tag tag = tagService.findOrCreateNew(tagForm.getTagTitle());
        studySettingsService.addTag(study, tag);
        return ResponseEntity.ok().build();
    }

    /**
     * [POST] 스터디에서 태그를 제거합니다. (AJAX 요청)
     *
     * 제거하려는 태그가 DB에 존재하지 않으면 400 Bad Request를 반환합니다.
     */
    @PostMapping("/tags/remove")
    @ResponseBody
    public ResponseEntity removeTag(Account account,
                                    @PathVariable String path,
                                    @RequestBody TagForm tagForm) {
        Study study = studySettingsService.getStudyToUpdateTag(account, path);
        Tag tag = tagService.findByTitle(tagForm.getTagTitle());

        if (tag == null) return ResponseEntity.badRequest().build();

        studySettingsService.removeTag(study, tag);
        return ResponseEntity.ok().build();
    }

    // ============================
    // 10~12. 지역 설정 (Zones)
    // ============================

    /**
     * [GET] 활동 지역 설정 화면을 렌더링합니다.
     *
     * 태그 설정과 동일한 패턴으로 Tagify 라이브러리와 연동합니다.
     * ZoneService.getAllZoneNames()를 통해 Zone -> String 변환을 서비스 계층에서 처리합니다.
     */
    @GetMapping("/zones")
    public String studyZonesForm(Account account,
                                 @PathVariable String path, Model model) throws JsonProcessingException {
        Study study = studySettingsService.getStudyToUpdate(account, path);
        addCommonAttributes(model, account, study);

        model.addAttribute("zones",
                study.getZones()
                        .stream()
                        .map(Zone::toString)
                        .collect(Collectors.toList()));

        List<String> allZoneNames = zoneService.getAllZoneNames();
        model.addAttribute("whitelist", objectMapper.writeValueAsString(allZoneNames));

        return "study/settings/zones";
    }

    /**
     * [POST] 스터디에 활동 지역을 추가합니다. (AJAX 요청)
     *
     * ZoneService.findByCityAndProvince()로 지역을 조회하며,
     * 존재하지 않는 지역이면 400 Bad Request를 반환합니다.
     *
     * Zone은 CSV 파일에서 미리 로드된 "폐쇄형 데이터"이므로
     * Tag처럼 새로 생성하지 않고 조회만 수행합니다.
     */
    @PostMapping("/zones/add")
    @ResponseBody
    public ResponseEntity addZone(
            Account account,
            @RequestBody ZoneForm zoneForm,
            @PathVariable String path
    ) {
        Study study = studySettingsService.getStudyToUpdateZone(account, path);

        Zone zone = zoneService.findByCityAndProvince(zoneForm.getCityName(), zoneForm.getProvinceName());
        if (zone == null) return ResponseEntity.badRequest().build();

        studySettingsService.addZone(study, zone);
        return ResponseEntity.ok().build();
    }

    /**
     * [POST] 스터디에서 활동 지역을 제거합니다. (AJAX 요청)
     */
    @PostMapping("/zones/remove")
    @ResponseBody
    public ResponseEntity removeZone(
            Account account,
            @PathVariable String path,
            @RequestBody ZoneForm zoneForm
    ) {
        Study study = studySettingsService.getStudyToUpdateZone(account, path);

        Zone zone = zoneService.findByCityAndProvince(zoneForm.getCityName(), zoneForm.getProvinceName());
        if (zone == null) return ResponseEntity.badRequest().build();

        studySettingsService.removeZone(study, zone);
        return ResponseEntity.ok().build();
    }

    // ============================
    // 13~15. 스터디 상태 관리 (공개/종료)
    // ============================

    /**
     * [GET] 스터디 상태 관리 페이지를 렌더링합니다.
     *
     * 이 페이지에서는 다음 기능들을 제공합니다:
     * - 스터디 공개/종료
     * - 팀원 모집 시작/중단
     * - 경로/제목 변경
     * - 스터디 삭제
     */
    @GetMapping("/study")
    public String studySettingForm(
            Account account,
            @PathVariable String path,
            Model model
    ) {
        Study study = studySettingsService.getStudyToUpdate(account, path);
        addCommonAttributes(model, account, study);
        return "study/settings/study";
    }

    /**
     * [POST] 스터디를 공개 처리합니다.
     *
     * 공개 처리 시 StudyCreatedEvent가 발행되어,
     * 관심 태그/지역이 일치하는 사용자들에게 알림이 전송됩니다.
     */
    @PostMapping("/study/publish")
    public String publishStudy(
            Account account,
            @PathVariable String path,
            RedirectAttributes redirectAttributes
    ) {
        Study study = studySettingsService.getStudyToUpdateStatus(account, path);
        studySettingsService.publish(study);
        redirectAttributes.addFlashAttribute("message", "스터디를 공개했습니다.");
        return redirectTo(study, "study");
    }

    /**
     * [POST] 스터디를 종료 처리합니다.
     */
    @PostMapping("/study/close")
    public String closeStudy(
            Account account,
            @PathVariable String path,
            RedirectAttributes redirectAttributes
    ) {
        Study study = studySettingsService.getStudyToUpdateStatus(account, path);
        studySettingsService.close(study);
        redirectAttributes.addFlashAttribute("message", "스터디를 종료했습니다.");
        return redirectTo(study, "study");
    }

    // ============================
    // 16~17. 팀원 모집 관리
    // ============================

    /**
     * [POST] 팀원 모집을 시작합니다.
     *
     * 모집 상태 변경에는 1시간 쿨다운이 적용됩니다.
     * 컨트롤러에서 canUpdateRecruiting()을 먼저 확인하여
     * 사용자에게 친절한 flash 메시지를 제공합니다.
     *
     * 서비스 계층의 study.startRecruit() 내부에서도 동일한 검증이 수행되지만,
     * 컨트롤러 레벨에서 사전 검증하는 이유는 사용자 경험(UX) 때문입니다:
     * - 서비스에서 RuntimeException이 발생하면 에러 페이지가 표시되지만,
     * - 컨트롤러에서 사전 검증하면 flash 메시지로 안내할 수 있습니다.
     */
    @PostMapping("/recruit/start")
    public String startRecruit(
            Account account,
            Model model,
            @PathVariable String path,
            RedirectAttributes redirectAttributes
    ) {
        Study study = studySettingsService.getStudyToUpdateStatus(account, path);

        if (!study.canUpdateRecruiting()) {
            redirectAttributes.addFlashAttribute("message", "1시간 안에 인원 모집 설정을 여러번 변경할 수 없습니다.");
            return redirectTo(study, "study");
        }

        studySettingsService.startRecruit(study);
        redirectAttributes.addFlashAttribute("message", "인원 모집을 시작합니다.");
        return redirectTo(study, "study");
    }

    /**
     * [POST] 팀원 모집을 중단합니다.
     */
    @PostMapping("/recruit/stop")
    public String stopRecruit(
            Account account,
            @PathVariable String path,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        Study study = studySettingsService.getStudyToUpdateStatus(account, path);

        if (!study.canUpdateRecruiting()) {
            redirectAttributes.addFlashAttribute("message", "1시간 안에 인원 모집 설정을 여러번 변경할 수 없습니다.");
            return redirectTo(study, "study");
        }

        studySettingsService.stopRecruit(study);
        redirectAttributes.addFlashAttribute("message", "인원 모집을 종료합니다.");
        return redirectTo(study, "study");
    }

    // ============================
    // 18~19. 경로 / 제목 변경
    // ============================

    /**
     * [POST] 스터디 경로(path)를 변경합니다.
     *
     * 유효성 검증 실패 시 폼을 다시 렌더링하면서 에러 메시지를 표시합니다.
     * 성공 시 변경된 경로 기반으로 리다이렉트합니다.
     *
     * 참고: isValidPath()는 정규식 패턴 검증 + DB 중복 검증을 모두 수행합니다.
     */
    @PostMapping("/study/path")
    public String updateStudyPath(
            Account account,
            @PathVariable String path,
            String newPath,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        Study study = studySettingsService.getStudyToUpdateStatus(account, path);

        if (!studySettingsService.isValidPath(newPath)) {
            model.addAttribute(account);
            model.addAttribute(study);
            model.addAttribute("studyPathError", "해당 스터디 경로는 사용할 수 없습니다. 다른 값을 입력하세요.");
            return "study/settings/study";
        }

        studySettingsService.updateStudyPath(study, newPath);
        redirectAttributes.addFlashAttribute("message", "스터디 경로를 수정했습니다.");
        return redirectTo(study, "study");
    }

    /**
     * [POST] 스터디 제목(title)을 변경합니다.
     */
    @PostMapping("/study/title")
    public String updateStudyTitle(
            Account account,
            @PathVariable String path,
            String newTitle,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        Study study = studySettingsService.getStudyToUpdateStatus(account, path);

        if (!studySettingsService.isValidTitle(newTitle)) {
            model.addAttribute(account);
            model.addAttribute(study);
            model.addAttribute("studyTitleError", "스터디 이름을 다시 입력하세요.");
            return "study/settings/study";
        }

        studySettingsService.updateStudyTitle(study, newTitle);
        redirectAttributes.addFlashAttribute("message", "스터디 이름을 수정했습니다.");
        return redirectTo(study, "study");
    }

    // ============================
    // 20. 스터디 삭제
    // ============================

    /**
     * [POST] 스터디를 삭제합니다.
     *
     * 삭제 조건: 아직 공개(publish)되지 않은 스터디만 삭제 가능합니다.
     * 공개된 스터디는 참여자가 존재할 수 있으므로 삭제가 불가능합니다.
     * 삭제 후 홈 페이지로 리다이렉트합니다.
     */
    @PostMapping("/study/remove")
    public String removeStudy(
            Account account,
            @PathVariable String path,
            Model model
    ) {
        Study study = studySettingsService.getStudyToUpdateStatus(account, path);
        studySettingsService.remove(study);
        return "redirect:/";
    }

    // ============================
    // 가입 신청 관리
    // ============================

    /**
     * [GET] 가입 신청 관리 페이지를 렌더링합니다.
     *
     * 스터디 설정 사이드바에서 "가입 관리" 탭에 해당합니다.
     * - 가입 방식(OPEN/APPROVAL_REQUIRED) 설정 폼
     * - 대기 중인 가입 신청 목록 (승인/거절 버튼 포함)
     *
     * getStudyToUpdate()를 사용하는 이유:
     * - findByPath()로 모든 연관 엔티티를 로딩 (managers 포함)
     * - 관리자 권한 검증이 내부에서 수행됨
     */
    @GetMapping("/join-requests")
    public String joinRequestsForm(Account account,
                                   @PathVariable String path,
                                   Model model) {
        Study study = studySettingsService.getStudyToUpdate(account, path);
        addCommonAttributes(model, account, study);

        List<JoinRequest> pendingRequests = studySettingsService.getPendingJoinRequests(study);
        model.addAttribute("pendingRequests", pendingRequests);
        model.addAttribute("pendingCount", pendingRequests.size());

        return "study/settings/join-requests";
    }

    /**
     * [POST] 가입 신청을 승인합니다.
     * <p>
     * 처리 흐름:
     * 1. JoinRequest.approve() -> status를 APPROVED로 변경
     * 2. Study.addMember() -> 기존 멤버 등록 로직 재활용
     * 3. StudyUpdateEvent 발행 -> 관련 사용자에게 알림
     * <p>
     * getStudyToUpdate()를 사용하는 이유:
     * - 승인 시 study.addMember()를 호출하려면 members 컬렉션이 로딩되어 있어야 함
     * - findByPath()는 모든 연관 엔티티(members 포함)를 fetch join
     */
    @PostMapping("/join-requests/{requestId}/approve")
    public String approveJoinRequest(Account account,
                                     @PathVariable String path,
                                     @PathVariable Long requestId,
                                     RedirectAttributes redirectAttributes) {
        Study study = studySettingsService.getStudyToUpdate(account, path);
        studySettingsService.approveJoinRequest(requestId, study);
        redirectAttributes.addFlashAttribute("message", "가입 신청을 승인했습니다.");
        return redirectTo(study, "join-requests");
    }

    /**
     * [POST] 가입 신청을 거절합니다.
     *
     * 거절 시에는 Study.addMember()가 호출되지 않습니다.
     * JoinRequest.reject()로 상태만 REJECTED로 변경합니다.
     */
    @PostMapping("/join-requests/{requestId}/reject")
    public String rejectJoinRequest(Account account,
                                    @PathVariable String path,
                                    @PathVariable Long requestId,
                                    RedirectAttributes redirectAttributes) {
        Study study = studySettingsService.getStudyToUpdate(account, path);
        studySettingsService.rejectJoinRequest(requestId, study);
        redirectAttributes.addFlashAttribute("message", "가입 신청을 거절했습니다.");
        return redirectTo(study, "join-requests");
    }

    /**
     * [POST] 스터디 가입 방식을 변경합니다.
     *
     * getStudyToUpdateStatus()를 사용하는 이유:
     * - 가입 방식 변경은 Study 자체의 상태 필드만 수정하면 됨
     * - managers만 fetch join하여 관리자 권한만 확인 (가볍게 조회)
     * - 기존 publish/close/recruit 패턴과 동일한 레벨의 설정 변경
     */
    @PostMapping("/join-type")
    public String updateJoinType(Account account,
                                 @PathVariable String path,
                                 @RequestParam JoinType joinType,
                                 RedirectAttributes redirectAttributes) {
        Study study = studySettingsService.getStudyToUpdate(account, path);
        studySettingsService.updateJoinType(study, joinType);
        String typeLabel = joinType == JoinType.OPEN ? "자유 가입" : "승인제";
        redirectAttributes.addFlashAttribute("message", "가입 방식을 '" + typeLabel + "'으로 변경했습니다.");
        return redirectTo(study, "join-requests");
    }
}