package com.studyolle.modules.account.controller;

import com.studyolle.modules.account.entity.Account;
import com.studyolle.modules.account.dto.Profile;
import com.studyolle.modules.account.security.CurrentUser;
import com.studyolle.modules.account.service.AccountSettingsService;
import com.studyolle.modules.study.entity.Study;
import com.studyolle.modules.study.service.StudyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * ✅ 프로필 조회 및 수정을 담당하는 컨트롤러
 * <p>
 * 담당 기능:
 * - 프로필 조회 (GET /profile/{nickname}) — 공개 접근 가능
 * - 프로필 수정 폼 렌더링 (GET /settings/profile) — 인증 필요
 * - 프로필 수정 처리 (POST /settings/profile) — 인증 필요
 * <p>
 * 설계 의도:
 * - "프로필"이라는 하나의 도메인 개념에 대한 조회/수정을 하나로 응집
 * - 조회는 비인증 사용자도 접근 가능 (공개 프로필)
 * - 수정은 본인만 가능 (@CurrentUser로 인증된 사용자 확인)
 * - 프로필 조회 시 "자기 자신의 프로필인지" 여부를 판별하여 뷰에 전달
 * <p>
 * 의존 서비스:
 * - AccountSettingsService: 프로필 수정, 닉네임 기반 계정 조회
 */
@Controller
@RequiredArgsConstructor
public class ProfileController {

    private final AccountSettingsService accountSettingsService;
    private final ModelMapper modelMapper;
    private final StudyService studyService;

    /**
     * ✅ 사용자 프로필 페이지 조회 (공개)
     * <p>
     * - URL 경로 변수 {nickname}을 통해 특정 사용자의 프로필을 조회
     * - SecurityConfig에서 GET /profile/* 은 permitAll()로 설정되어 비인증 접근 가능
     * <p>
     * 뷰에 전달하는 데이터:
     * - account: 조회 대상 사용자의 Account 객체
     * - isOwner: 현재 로그인한 사용자와 조회 대상이 동일한지 여부 (Boolean)
     * → 뷰에서 "프로필 수정" 버튼 표시 여부 등에 사용
     *
     * @param nickname 조회 대상 사용자의 닉네임 (경로 변수)
     * @param model    View에 전달할 데이터
     * @param account  현재 로그인한 사용자 (비로그인 시 null 가능)
     */
    @GetMapping("/profile/{nickname}")
    public String viewProfile(@PathVariable String nickname, Model model, @CurrentUser Account account) {

        // accountSettingsService.getAccount() 내부에서 존재하지 않는 닉네임이면 예외 발생
        Account accountToView = accountSettingsService.getAccount(nickname);

        // 모델에 키 없이 객체를 추가하면 클래스명의 camelCase가 키가 됨 → "account"
        model.addAttribute(accountToView);

        // equals()는 Account 도메인에서 id 기준으로 재정의되어 있어 올바르게 비교 가능
        model.addAttribute("isOwner", accountToView.equals(account));

        List<Study> studyManagerOf = studyService.getStudiesAsManager(accountToView);
        List<Study> studyMemberOf = studyService.getStudiesAsMember(accountToView);

        model.addAttribute("studyManagerOf", studyManagerOf);
        model.addAttribute("studyMemberOf", studyMemberOf);

        // View: resources/templates/account/profile.html
        return "account/profile";
    }

    /**
     * ✅ 프로필 수정 폼 렌더링
     * <p>
     * - 현재 로그인한 사용자의 Account 정보를 Profile DTO로 변환하여 폼에 바인딩
     * - ModelMapper를 사용하여 Account → Profile 필드 자동 매핑
     * (url, bio, occupation, location, profileImage 등)
     * <p>
     * View: resources/templates/settings/profile.html
     */
    @GetMapping("/settings/profile")
    public String updateProfileForm(@CurrentUser Account account, Model model) {
        model.addAttribute("account", account);
        model.addAttribute(modelMapper.map(account, Profile.class));
        return "settings/profile";
    }

    /**
     * ✅ 프로필 수정 처리 (POST)
     * <p>
     * 처리 흐름:
     * 1. @Valid를 통해 Profile DTO의 Bean Validation 검증
     * 2. 검증 실패 시 → 수정 폼으로 되돌아감 (account 정보를 다시 모델에 추가)
     * 3. 검증 성공 시 → AccountSettingsService에서 프로필 업데이트
     * - 내부적으로 ModelMapper를 사용하여 Profile DTO → Account 엔티티 필드 매핑
     * - Account는 Detached 상태이므로 accountRepository.save()를 호출하여 DB 반영
     * 4. 성공 메시지를 Flash Attribute로 전달 후 리다이렉트
     *
     * @param account            현재 로그인한 사용자 (@CurrentUser로 SecurityContext에서 추출)
     * @param profile            폼에서 입력받은 프로필 수정 데이터
     * @param errors             검증 결과
     * @param model              에러 시 account 정보 재전달용
     * @param redirectAttributes 성공 메시지 전달용 (Flash Attribute)
     */
    @PostMapping("/settings/profile")
    public String updateProfile(@CurrentUser Account account, @Valid @ModelAttribute Profile profile,
                                Errors errors, Model model, RedirectAttributes redirectAttributes) {
        if (errors.hasErrors()) {
            model.addAttribute(account);
            return "settings/profile";
        }
        accountSettingsService.updateProfile(account, profile);
        redirectAttributes.addFlashAttribute("message", "프로필을 수정했습니다.");
        return "redirect:/settings/profile";
    }

    @GetMapping("/my-studies")
    public String myStudies(@CurrentUser Account account) {
        return "redirect:/profile/" + account.getNickname() + "#study";
    }
}