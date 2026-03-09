package com.studyolle.modules.study.controller;

import com.studyolle.modules.account.entity.Account;
import com.studyolle.modules.study.entity.Study;
import com.studyolle.modules.study.dto.StudyForm;
import com.studyolle.modules.study.service.StudyService;
import com.studyolle.modules.event.entity.Event;
import com.studyolle.modules.event.repository.EventRepository;
import com.studyolle.modules.study.validator.StudyFormValidator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.Errors;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * StudyController - 스터디 생성, 조회, 멤버 가입/탈퇴를 처리하는 컨트롤러
 *
 * =============================================
 * 담당 기능
 * =============================================
 *
 * 1. 스터디 생성 (GET/POST /new-study)
 *    - 생성 폼 표시 및 제출 처리
 *    - StudyFormValidator를 통한 path 중복 검증
 *
 * 2. 스터디 조회 (GET /study/{path}, /study/{path}/members)
 *    - 상세 페이지 및 멤버 목록 페이지 렌더링
 *
 * 3. 멤버 가입/탈퇴 (GET /study/{path}/join, /study/{path}/leave)
 *    - 현재 사용자를 스터디 멤버로 추가/제거
 *
 * =============================================
 * 계층 구조 (Controller -> Service -> Repository)
 * =============================================
 *
 * 이 컨트롤러는 StudyService만을 통해 데이터에 접근합니다.
 * Repository에 직접 접근하지 않으며, 모든 비즈니스 로직은 서비스 계층에 위임합니다.
 *
 * [리팩터링 내역]
 * 기존에는 joinStudy(), leaveStudy() 메서드에서
 * studyRepository.findStudyWithMembersByPath(path)를 직접 호출하는 계층 위반이 있었습니다.
 * 이를 StudyService.getStudyWithMembersByPath(path)로 변경하여 계층 원칙을 준수합니다.
 *
 * [리팩터링 내역]
 * 기존에는 StudyRepository를 필드로 주입받았으나,
 * 모든 호출이 서비스를 통하도록 변경되었으므로 StudyRepository 의존성을 제거했습니다.
 *
 * =============================================
 * @CurrentUser 어노테이션과 Account 파라미터 주입
 * =============================================
 *
 * 모든 핸들러 메서드의 Account 파라미터에는 반드시 @CurrentUser 어노테이션이 필요합니다.
 *
 * @CurrentUser는 다음과 같은 동작을 수행하는 커스텀 어노테이션입니다:
 *   1. SecurityContext에서 현재 인증된 principal(UserAccount) 추출
 *   2. SpEL expression으로 UserAccount.getAccount() 호출
 *   3. Account 객체를 컨트롤러 파라미터에 주입
 *   4. 비로그인 사용자인 경우 null 주입
 *
 * @CurrentUser 없이 Account를 파라미터로 선언하면 Spring은 이것을
 * HTTP 요청 파라미터에서 바인딩하려고 시도하므로, 빈 Account 객체가 생성되거나
 * id가 null인 상태로 주입되어 예상치 못한 오류가 발생합니다.
 */
@Controller
@RequiredArgsConstructor
public class StudyController {

    private final StudyService studyService;
    private final EventRepository eventRepository;
    private final ModelMapper modelMapper;
    private final StudyFormValidator studyFormValidator;

    /**
     * WebDataBinder에 커스텀 validator를 등록합니다.
     *
     * @InitBinder("studyForm")은 모델에서 "studyForm"이라는 이름의 속성이
     * 바인딩될 때만 이 validator가 적용되도록 범위를 제한합니다.
     *
     * StudyFormValidator는 path 필드의 중복 여부를 DB에서 확인합니다.
     * Bean Validation(@Pattern, @Length 등)으로는 DB 조회가 필요한
     * 비즈니스 규칙을 검증할 수 없으므로, 커스텀 validator가 필요합니다.
     */
    @InitBinder("studyForm")
    public void studyFormInitBinder(WebDataBinder webDataBinder) {
        webDataBinder.addValidators(studyFormValidator);
    }

    // ============================
    // 스터디 생성
    // ============================

    /**
     * [GET] 스터디 개설 폼 페이지를 렌더링합니다.
     *
     * @param account 현재 로그인한 사용자 (@CurrentUser로 SecurityContext에서 주입)
     * @param model   뷰에 전달할 모델 객체
     * @return study/form 뷰 이름
     */
    @GetMapping("/new-study")
    public String newStudyForm(Account account, Model model) {
        model.addAttribute("account", account);
        model.addAttribute(new StudyForm());
        return "study/form";
    }

    /**
     * [POST] 스터디 개설 폼 제출을 처리합니다.
     *
     * 처리 흐름:
     * 1. Bean Validation + StudyFormValidator로 유효성 검증
     * 2. 검증 실패 시 폼 다시 렌더링
     * 3. 검증 성공 시 StudyForm -> Study 엔티티 변환 후 스터디 생성
     * 4. 생성된 스터디의 상세 페이지로 리다이렉트
     *
     * @param account   현재 로그인한 사용자 (관리자로 등록됨)
     * @param studyForm 사용자가 입력한 스터디 생성 폼 데이터
     * @param errors    유효성 검증 결과
     * @param model     뷰에 전달할 모델 객체
     * @return 성공 시 리다이렉트 URL, 실패 시 study/form 뷰 이름
     */
    @PostMapping("/new-study")
    public String newStudySubmit(Account account, @Valid StudyForm studyForm, Errors errors, Model model) {
        if (errors.hasErrors()) {
            model.addAttribute("account", account);
            return "study/form";
        }

        Study newStudy = studyService.createNewStudy(modelMapper.map(studyForm, Study.class), account);
        return "redirect:/study/" + URLEncoder.encode(newStudy.getPath(), StandardCharsets.UTF_8);
    }

    // ============================
    // 스터디 조회
    // ============================

    /**
     * [GET] 스터디 상세 페이지를 렌더링합니다.
     *
     * [통합 뷰 리팩터링]
     * 기존에는 소개/구성원/모임이 각각 별도 탭 페이지였으나,
     * 사용성 개선을 위해 한 페이지에 통합하여 보여줍니다.
     * - 소개 (fullDescription)
     * - 구성원 (managers + members)
     * - 모임 (newEvents + oldEvents)
     * 설정만 별도 페이지로 분리됩니다.
     *
     * [가입 신청 시스템 추가사항]
     * 승인제 스터디일 때, 현재 사용자가 이미 신청했는지를 모델에 추가합니다.
     * 이 값으로 뷰(fragments.html study-info)에서 버튼을 분기합니다:
     * - hasPendingRequest == true  -> "신청 중" 버튼 (비활성)
     * - hasPendingRequest == false -> "가입 신청" 버튼
     * - 자유 가입 스터디               -> "가입하기" 버튼 (기존과 동일)
     *
     * account != null 체크 이유:
     * 비로그인 사용자는 신청 자체가 불가능하므로 조회할 필요가 없습니다.
     */
    @GetMapping("/study/{path}")
    public String viewStudy(Account account, @PathVariable String path, Model model) {
        Study study = studyService.getStudy(path);
        model.addAttribute("account", account);
        model.addAttribute(study);

        // 승인제 스터디: 현재 사용자의 대기 중인 신청 여부 확인
        if (account != null && study.isApprovalRequired()) {
            model.addAttribute("hasPendingRequest", studyService.hasPendingJoinRequest(study, account));
        }

        // 이벤트 데이터 로드 (newEvents / oldEvents 분류)
        List<Event> events = eventRepository.findByStudyOrderByStartDateTime(study);
        List<Event> newEvents = new ArrayList<>();
        List<Event> oldEvents = new ArrayList<>();
        events.forEach(e -> {
            if (e.getEndDateTime().isBefore(LocalDateTime.now())) {
                oldEvents.add(e);
            } else {
                newEvents.add(e);
            }
        });
        model.addAttribute("newEvents", newEvents);
        model.addAttribute("oldEvents", oldEvents);

        return "study/view";
    }

    /**
     * [GET] 스터디 멤버 목록 페이지를 렌더링합니다.
     */
    @GetMapping("/study/{path}/members")
    public String viewStudyMembers(Account account, @PathVariable String path, Model model) {
        Study study = studyService.getStudy(path);
        model.addAttribute("account", account);
        model.addAttribute(study);
        return "study/members";
    }

    // ============================
    // 멤버 가입 / 탈퇴
    // ============================

    /**
     * [GET] 스터디 참가 요청을 처리합니다.
     *
     * [가입 신청 시스템 변경사항]
     * 기존에는 무조건 즉시 멤버로 등록했지만,
     * 이제 study.isApprovalRequired()로 가입 방식을 확인하여 분기합니다:
     *
     * - OPEN (자유 가입): 기존과 동일하게 즉시 addMember()
     * - APPROVAL_REQUIRED (승인제): JoinRequest 생성 후 리다이렉트
     *
     * 기존 코드(addMember)는 그대로 두고,
     * 앞에 분기 하나만 추가하는 구조입니다.
     *
     * @param account 현재 로그인한 사용자
     * @param path    스터디 URL 경로
     * @return 스터디 상세 페이지로 리다이렉트
     */
    @GetMapping("/study/{path}/join")
    public String joinStudy(Account account,
                            @PathVariable String path,
                            RedirectAttributes redirectAttributes) {
        Study study = studyService.getStudyWithMembersByPath(path);

        if (study.isApprovalRequired()) {
            // 승인제: 신청만 생성하고 멤버 등록은 관리자 승인 시 처리됨
            try {
                studyService.createJoinRequest(study, account);
                redirectAttributes.addFlashAttribute("message", "가입 신청이 완료되었습니다. 관리자의 승인을 기다려 주세요.");
            } catch (IllegalStateException e) {
                // 중복 신청 방지: 이미 대기 중인 신청이 있을 때
                redirectAttributes.addFlashAttribute("message", e.getMessage());
            }
            return "redirect:/study/" + study.getEncodedPath();
        }

        // 자유 가입: 기존과 동일하게 즉시 멤버 등록
        studyService.addMember(study, account);
        return "redirect:/study/" + study.getEncodedPath();
    }

    /**
     * [GET] 스터디 탈퇴 요청을 처리합니다.
     */
    @GetMapping("/study/{path}/leave")
    public String leaveStudy(Account account, @PathVariable String path) {
        Study study = studyService.getStudyWithMembersByPath(path);
        studyService.removeMember(study, account);
        return "redirect:/study/" + study.getEncodedPath();
    }
}