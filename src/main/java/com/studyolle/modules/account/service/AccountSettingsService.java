package com.studyolle.modules.account.service;

import com.studyolle.modules.account.entity.Account;
import com.studyolle.modules.account.dto.Notifications;
import com.studyolle.modules.account.dto.Profile;
import com.studyolle.modules.account.repository.AccountRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ✅ 계정 정보 조회 및 수정을 담당하는 서비스
 *
 * 담당 기능:
 *   - 프로필 수정 (updateProfile)
 *   - 비밀번호 변경 (updatePassword)
 *   - 닉네임 변경 (updateNickname)
 *   - 알림 설정 변경 (updateNotifications)
 *   - 닉네임 기반 계정 조회 (getAccount)
 *
 * 설계 의도:
 *   - ProfileController와 MyAccountController가 공통으로 사용하는 서비스
 *   - "계정 정보를 조회하거나 수정한다"는 하나의 비즈니스 맥락으로 응집
 *
 * Detached 상태 처리:
 *   - @CurrentUser로 주입받는 Account 객체는 SecurityContext에서 가져온 것으로,
 *     JPA 영속성 컨텍스트와 무관한 Detached 상태임
 *   - 따라서 필드 변경 후 반드시 accountRepository.save(account)를 호출하여
 *     merge를 유발해야 DB에 반영됨 (Dirty Checking이 작동하지 않음)
 *
 * 호출 관계:
 *   - ProfileController → updateProfile(), getAccount()
 *   - MyAccountController → updatePassword(), updateNickname(), updateNotifications()
 *   - updateNickname() → AccountAuthService.login() (변경된 닉네임을 SecurityContext에 반영)
 */
@Service
@Transactional
@RequiredArgsConstructor
public class AccountSettingsService {

    private final AccountRepository accountRepository;
    private final ModelMapper modelMapper;
    private final PasswordEncoder passwordEncoder;
    private final AccountAuthService accountAuthService;

    /**
     * ✅ 프로필 수정
     *
     * - ModelMapper를 사용하여 Profile DTO → Account 엔티티 필드 자동 매핑
     *   (url, bio, occupation, location, profileImage 등)
     * - Account는 Detached 상태이므로 save() 호출로 DB에 반영
     *
     * @param account 현재 로그인한 사용자 (Detached 상태)
     * @param profile 폼에서 입력받은 프로필 수정 데이터
     */
    public void updateProfile(Account account, @Valid Profile profile) {
        modelMapper.map(profile, account);
        accountRepository.save(account);
    }

    /**
     * ✅ 비밀번호 변경
     *
     * - 평문 비밀번호를 BCrypt로 인코딩 후 Account에 설정
     * - Detached 상태이므로 save() 호출로 merge 유발 → DB에 UPDATE 쿼리 전송
     *
     * @param account     현재 로그인한 사용자 (Detached 상태)
     * @param newPassword 새 비밀번호 (평문)
     */
    public void updatePassword(Account account, String newPassword) {
        account.setPassword(passwordEncoder.encode(newPassword));
        accountRepository.save(account);
    }

    /**
     * ✅ 알림 설정 변경
     *
     * - ModelMapper를 사용하여 Notifications DTO → Account 엔티티 필드 자동 매핑
     *   (studyCreatedByEmail, studyCreatedByWeb, studyUpdatedByEmail 등)
     * - Detached 상태이므로 save() 호출로 DB에 반영
     *
     * @param account       현재 로그인한 사용자 (Detached 상태)
     * @param notifications 폼에서 입력받은 알림 설정 데이터
     */
    public void updateNotifications(Account account, @Valid Notifications notifications) {
        modelMapper.map(notifications, account);
        accountRepository.save(account);
    }

    /**
     * ✅ 닉네임 변경
     *
     * - 닉네임 필드 변경 후 DB에 저장
     * - 추가로 AccountAuthService.login()을 호출하여 SecurityContext의 인증 정보를 갱신
     *   → 네비게이션 바 등에서 변경된 닉네임이 즉시 반영되도록
     *   → 안 하면 세션에 저장된 옛날 닉네임이 화면에 계속 표시됨
     *
     * @param account  현재 로그인한 사용자 (Detached 상태)
     * @param nickname 새 닉네임
     */
    public void updateNickname(Account account, String nickname) {
        account.setNickname(nickname);
        accountRepository.save(account);
        accountAuthService.login(account);
    }

    /**
     * ✅ 닉네임으로 사용자(Account) 조회
     *
     * - 프로필 조회(GET /profile/{nickname}) 등에서 사용
     * - 닉네임은 공개 식별자이므로 이메일 대신 URL에 사용
     * - 해당 닉네임의 사용자가 없으면 IllegalArgumentException 예외 발생
     *
     * @param nickname 조회 대상 사용자의 닉네임
     * @return 조회된 Account 엔티티
     * @throws IllegalArgumentException 닉네임에 해당하는 사용자가 없을 경우
     */
    public Account getAccount(String nickname) {
        Account account = accountRepository.findByNickname(nickname);
        if (account == null) {
            throw new IllegalArgumentException(nickname + "에 해당하는 사용자가 없습니다.");
        }
        return account;
    }
}

/**
 * ✅ 계정 정보 조회 및 수정을 담당하는 서비스
 *
 * 담당 기능:
 *   - 프로필 수정 (updateProfile)
 *   - 비밀번호 변경 (updatePassword)
 *   - 닉네임 변경 (updateNickname)
 *   - 알림 설정 변경 (updateNotifications)
 *   - 닉네임 기반 계정 조회 (getAccount)
 *
 * ──────────────────────────────────────────────────────────────────
 * [핵심 설계 포인트] Detached 상태 + save(merge) 방식을 사용하는 이유
 * ──────────────────────────────────────────────────────────────────
 *
 * 이 서비스의 수정 메서드는 다음 패턴을 따른다:
 *
 *   account.setPassword(passwordEncoder.encode(newPassword));
 *   accountRepository.save(account);   // merge 유발 -> DB에 UPDATE
 *
 * 왜 findById()로 재조회하지 않는가?
 *   - 이 서비스가 다루는 필드는 password, nickname, bio, profileImage 등
 *     모두 Account 엔티티의 단순 스칼라 필드 (String, boolean 등)
 *   - 단순 필드는 @ManyToMany 연관관계와 달리 Lazy Loading이 필요 없으므로
 *     Detached 상태에서도 setPassword(), setNickname() 등을 문제없이 호출 가능
 *   - 따라서 findById()로 SELECT 쿼리를 한 번 더 날릴 필요가 없음
 *
 * 왜 save()가 필요한가?
 *   - @CurrentUser로 주입받는 Account는 SecurityContext(세션)에서 꺼낸 Detached 상태
 *   - Detached 상태에서는 JPA의 Dirty Checking이 작동하지 않음
 *     (영속성 컨텍스트가 관리하지 않으므로 변경 사항을 추적할 수 없음)
 *   - 따라서 save(account)를 명시적으로 호출하여 merge를 유발해야 DB에 반영됨
 *
 * TagZoneService와의 차이:
 *   - TagZoneService는 getTags()/getZones() 같은 @ManyToMany Lazy 컬렉션에 접근해야 함
 *   - Detached 상태에서 Lazy 컬렉션 접근 시 LazyInitializationException 발생
 *   - 그래서 findById()로 재조회가 강제되고, 그 결과 영속 상태가 되어 Dirty Checking 자동 적용
 *   - 즉, TagZoneService의 findById()는 "영속 상태를 만들기 위해서"가 아니라
 *     "Lazy 컬렉션에 접근하기 위해" 어쩔 수 없이 필요한 것
 *
 * 정리:
 *   - 이 서비스: Lazy 컬렉션 접근 불필요 → findById() 생략 → save(merge)로 DB 반영
 *   - TagZoneService: Lazy 컬렉션 접근 필수 → findById() 강제 → Dirty Checking 자동 적용
 *
 * 호출 관계:
 *   - ProfileController → updateProfile(), getAccount()
 *   - MyAccountController → updatePassword(), updateNickname(), updateNotifications()
 *   - updateNickname() → AccountAuthService.login() (변경된 닉네임을 SecurityContext에 반영)
 */