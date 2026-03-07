package com.studyolle.modules.account.controller;

import com.studyolle.modules.account.entity.Account;
import com.studyolle.modules.account.dto.NicknameForm;
import com.studyolle.modules.account.dto.Notifications;
import com.studyolle.modules.account.dto.PasswordForm;
import com.studyolle.modules.account.security.CurrentUser;
import com.studyolle.modules.account.service.AccountSettingsService;
import com.studyolle.modules.account.validator.NicknameValidator;
import com.studyolle.modules.account.validator.PasswordFormValidator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.Errors;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * ✅ 내 계정 정보 변경을 담당하는 컨트롤러
 *
 * 담당 기능:
 *   - 비밀번호 변경 (GET/POST /settings/password)
 *   - 알림 설정 변경 (GET/POST /settings/notifications)
 *   - 닉네임 변경 (GET/POST /settings/account)
 *
 * 설계 의도:
 *   - "내 계정 정보를 수정한다"는 공통 맥락을 가진 기능들을 하나의 컨트롤러로 응집
 *   - 프로필(bio, url 등 공개 정보)은 ProfileController에서 담당
 *   - 태그/지역(관심사)는 TagZoneController에서 담당
 *   - 이 컨트롤러는 비밀번호, 알림 설정, 닉네임 등 "계정 내부 설정" 변경에 집중
 *
 * URL 설계:
 *   - 모든 엔드포인트가 /settings 하위에 위치하므로 @RequestMapping("/settings") 적용
 *
 * 의존 서비스:
 *   - AccountSettingsService: 비밀번호/닉네임/알림 설정 변경
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/settings")
public class MyAccountController {

    private final AccountSettingsService accountSettingsService;
    private final ModelMapper modelMapper;
    private final NicknameValidator nicknameValidator;

    /**
     * PasswordForm에 대한 커스텀 Validator 등록
     *
     * - @InitBinder("passwordForm")는 이름이 "passwordForm"인 @ModelAttribute에만 적용
     * - PasswordFormValidator는 "새 비밀번호"와 "새 비밀번호 확인"의 일치 여부를 검증
     * - Bean Validation(@Valid)과 함께 실행되어 순차적으로 검증이 이루어짐
     */
    @InitBinder("passwordForm")
    public void passwordFormInitBinder(WebDataBinder binder) {
        binder.addValidators(new PasswordFormValidator());
    }

    /**
     * NicknameForm에 대한 커스텀 Validator 등록
     *
     * - NicknameValidator는 닉네임 중복 검사를 위해 DB 조회가 필요하므로
     *   Spring 빈으로 주입받아 사용 (new로 생성하지 않음)
     * - 닉네임 패턴 검증(@Pattern)은 NicknameForm의 Bean Validation에서 처리
     */
    @InitBinder("nicknameForm")
    public void nicknameFormInitBinder(WebDataBinder binder) {
        binder.addValidators(nicknameValidator);
    }

    // ──────────────────────────────────────────
    // 비밀번호 변경
    // ──────────────────────────────────────────

    /**
     * ✅ 비밀번호 변경 폼 렌더링
     *
     * - 빈 PasswordForm 객체를 모델에 추가하여 Thymeleaf 폼 바인딩에 사용
     * - account 정보는 네비게이션 표시 등에 필요
     * - View: resources/templates/settings/password.html
     */
    @GetMapping("/password")
    public String updatePasswordForm(@CurrentUser Account account, Model model) {
        model.addAttribute(account);
        model.addAttribute(new PasswordForm());
        return "settings/password";
    }

    /**
     * ✅ 비밀번호 변경 처리 (POST)
     *
     * 처리 흐름:
     *   1. Bean Validation + PasswordFormValidator로 검증
     *      - 새 비밀번호 길이 제한 (8~50자)
     *      - 새 비밀번호 확인 일치 여부
     *   2. 검증 실패 시 → 폼으로 되돌아감
     *   3. 검증 성공 시 → AccountSettingsService에서 비밀번호 인코딩 후 DB 업데이트
     *      - 내부적으로 PasswordEncoder.encode()를 사용하여 BCrypt 해싱
     *
     * @param account      현재 로그인한 사용자
     * @param passwordForm 새 비밀번호 + 비밀번호 확인
     * @param errors       검증 결과
     * @param model        에러 시 account 재전달용
     * @param attributes   성공 메시지 전달용 (Flash Attribute)
     */
    @PostMapping("/password")
    public String updatePassword(@CurrentUser Account account, @Valid PasswordForm passwordForm,
                                 Errors errors, Model model, RedirectAttributes attributes) {
        if (errors.hasErrors()) {
            model.addAttribute(account);
            return "settings/password";
        }
        accountSettingsService.updatePassword(account, passwordForm.getNewPassword());
        attributes.addFlashAttribute("message", "패스워드를 변경했습니다.");
        return "redirect:/settings/password";
    }

    // ──────────────────────────────────────────
    // 알림 설정
    // ──────────────────────────────────────────

    /**
     * ✅ 알림 설정 폼 렌더링
     *
     * - Account 엔티티의 알림 관련 필드를 Notifications DTO로 매핑하여 폼에 바인딩
     * - ModelMapper가 필드명 기준으로 자동 매핑
     *   (studyCreatedByEmail, studyCreatedByWeb, studyUpdatedByEmail 등)
     * - View: resources/templates/settings/notifications.html
     */
    @GetMapping("/notifications")
    public String updateNotificationsForm(@CurrentUser Account account, Model model) {
        model.addAttribute(account);
        model.addAttribute(modelMapper.map(account, Notifications.class));
        return "settings/notifications";
    }

    /**
     * ✅ 알림 설정 변경 처리 (POST)
     *
     * - Notifications DTO에 담긴 boolean 값들을 Account 엔티티에 반영
     * - 내부적으로 ModelMapper로 Notifications → Account 매핑 후 DB 저장
     */
    @PostMapping("/notifications")
    public String updateNotifications(@CurrentUser Account account, @Valid Notifications notifications,
                                      Errors errors, Model model, RedirectAttributes attributes) {
        if (errors.hasErrors()) {
            model.addAttribute(account);
            return "settings/notifications";
        }
        accountSettingsService.updateNotifications(account, notifications);
        attributes.addFlashAttribute("message", "알림 설정을 변경했습니다.");
        return "redirect:/settings/notifications";
    }

    // ──────────────────────────────────────────
    // 닉네임 변경
    // ──────────────────────────────────────────

    /**
     * ✅ 닉네임 변경 폼 렌더링
     *
     * - Account의 현재 닉네임을 NicknameForm DTO에 매핑하여 폼의 기본값으로 표시
     * - View: resources/templates/settings/account.html
     */
    @GetMapping("/account")
    public String updateAccountForm(@CurrentUser Account account, Model model) {
        model.addAttribute(account);
        model.addAttribute(modelMapper.map(account, NicknameForm.class));
        return "settings/account";
    }

    /**
     * ✅ 닉네임 변경 처리 (POST)
     *
     * 처리 흐름:
     *   1. Bean Validation(@Pattern) + NicknameValidator(중복 검사)로 검증
     *   2. 검증 실패 시 → 폼으로 되돌아감
     *   3. 검증 성공 시 → AccountSettingsService에서 닉네임 업데이트
     *      - 닉네임 변경 후 SecurityContext의 인증 정보도 갱신
     *        (네비게이션 바 등에서 변경된 닉네임이 즉시 반영되도록)
     */
    @PostMapping("/account")
    public String updateAccount(@CurrentUser Account account, @Valid NicknameForm nicknameForm,
                                Errors errors, Model model, RedirectAttributes attributes) {
        if (errors.hasErrors()) {
            model.addAttribute(account);
            return "settings/account";
        }
        accountSettingsService.updateNickname(account, nicknameForm.getNickname());
        attributes.addFlashAttribute("message", "닉네임을 수정했습니다.");
        return "redirect:/settings/account";
    }
}