package com.studyolle.account.service;

import com.studyolle.account.dto.response.AccountResponse;
import com.studyolle.account.dto.response.AccountSummaryResponse;
import com.studyolle.account.entity.Account;
import com.studyolle.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 내부 전용 (/internal/**) API 의 모든 비즈니스 로직을 담는 service.
 *
 * 조회와 쓰기 모두 이 service 를 경유한다.
 * controller 는 HTTP 어댑터 역할만 하고 Repository 에 직접 접근하지 않는다
 * — "Controller → Service → Repository" 라는 프로젝트의 3 계층 원칙을 일관되게 적용하기 위해서다.
 *
 * 조회 메서드는 모두 readOnly 트랜잭션으로 묶여 있다.
 * 쓰기 메서드(updateRole) 만 일반 @Transactional 이며 dirty checking 으로 UPDATE 가 발행된다.
 */
@Service
@RequiredArgsConstructor
public class AccountInternalService {

    private final AccountRepository accountRepository;

    // ──────────────────────────────────────────────────────────────────────────
    // 조회 API
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * id 로 계정 요약 정보를 조회한다. 대시보드/네비게이션 렌더링에 쓰인다.
     */
    @Transactional(readOnly = true)
    public AccountSummaryResponse getAccountSummary(Long id) {
        Account account = findAccountOrThrow(id);
        return AccountSummaryResponse.from(account);
    }

    /**
     * id 로 계정의 전체 정보(프로필 + 알림 설정 등) 를 조회한다.
     * 설정 페이지 초기 로드 시 사용된다.
     */
    @Transactional(readOnly = true)
    public AccountResponse getAccountFull(Long id) {
        Account account = findAccountOrThrow(id);
        return AccountResponse.from(account);
    }

    /**
     * id 에 해당하는 계정의 관심 태그 목록을 조회한다.
     * 컬렉션은 방어적 복사를 거쳐 반환한다 — 호출 측이 컬렉션을 변경하더라도
     * 영속성 컨텍스트의 원본 컬렉션이 영향을 받지 않게 하기 위함이다.
     */
    @Transactional(readOnly = true)
    public List<String> getAccountTags(Long id) {
        Account account = findAccountOrThrow(id);
        return new ArrayList<>(account.getTags());
    }

    /**
     * id 에 해당하는 계정의 활동 지역 목록을 조회한다.
     * 태그와 동일하게 방어적 복사를 거친다.
     */
    @Transactional(readOnly = true)
    public List<String> getAccountZones(Long id) {
        Account account = findAccountOrThrow(id);
        return new ArrayList<>(account.getZones());
    }

    /**
     * 닉네임으로 계정을 조회한다.
     * <p>
     * 다른 조회 메서드들과 달리 "없을 수 있음" 을 정상 케이스로 본다.
     * 따라서 IllegalArgumentException 을 던지지 않고 Optional 을 반환한다.
     * controller 는 이 결과를 보고 200(찾음) 또는 404(없음) 로 응답한다.
     * <p>
     * 왜 다른가:
     * id 기반 조회는 보통 "내가 방금 받은 토큰의 sub" 같이 이미 존재가 확인된 식별자로 들어온다.
     * 닉네임 기반 조회는 사용자가 URL 을 직접 치고 들어오는 케이스("/profile/없는닉네임") 가 있어서
     * 404 응답이 정상 흐름이다.
     */
    @Transactional(readOnly = true)
    public Optional<AccountSummaryResponse> getAccountByNickname(String nickname) {
        Account account = accountRepository.findByNickname(nickname);
        return Optional.ofNullable(account).map(AccountSummaryResponse::from);
    }

    /**
     * 관리자용 회원 목록 페이지네이션 조회.
     * <p>
     * keyword 가 null/blank 이면 전체 조회, 있으면 email 또는 nickname 부분 일치 검색.
     * Page.map() 은 totalElements/totalPages 등 페이지 메타데이터를 유지하면서
     * 요소 타입만 변환해 준다.
     */
    @Transactional(readOnly = true)
    public Page<AccountSummaryResponse> listAccounts(String keyword, Pageable pageable) {
        Page<Account> page;
        if (keyword == null || keyword.isBlank()) {
            page = accountRepository.findAll(pageable);
        } else {
            page = accountRepository.findByEmailContainingOrNicknameContaining(keyword, keyword, pageable);
        }
        return page.map(AccountSummaryResponse::from);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 쓰기 API
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * 회원 권한을 변경한다. admin-service 가 호출하는 내부 전용 메서드.
     *
     * @param targetId    권한을 변경할 대상 회원의 id
     * @param requesterId 요청한 관리자의 id (X-Account-Id 헤더에서 추출)
     * @param newRole     변경 후 권한 ("ROLE_ADMIN" | "ROLE_USER")
     * @return 변경된 회원의 요약 정보
     *
     * @throws IllegalArgumentException 요청자 정보 없음 / 자기 자신 변경 시도 / 잘못된 role 값 / 회원 없음
     *
     * [@Transactional 의 의미]
     * 메서드 진입 시 트랜잭션이 시작되고, 메서드가 정상 종료되면 커밋된다.
     * Hibernate 는 영속 엔티티(account) 의 필드가 바뀐 것을 감지(dirty checking)해
     * 커밋 시점에 자동으로 UPDATE 문을 발행한다. 따라서 별도의 save() 호출이 필요 없다.
     *
     * 만약 검증 단계에서 IllegalArgumentException 이 던져지면 트랜잭션은
     * Spring 의 기본 롤백 정책에 따라 RuntimeException 계열이므로 자동 롤백된다.
     * (어차피 그 시점에는 변경된 게 아무것도 없으니 롤백할 것도 없지만 의미상 안전하다.)
     */
    @Transactional
    public AccountSummaryResponse updateRole(Long targetId, Long requesterId, String newRole) {
        // 가드 1 — 요청자 정보가 없으면 거부.
        // 정상적으로 게이트웨이를 거쳐 들어왔다면 X-Account-Id 가 반드시 있어야 한다.
        // null 이면 헤더 없이 들어온 비정상 호출이다.
        if (requesterId == null) {
            throw new IllegalArgumentException("요청자 정보가 없습니다.");
        }

        // 가드 2 — 자기 자신 권한 변경 금지.
        // 관리자가 실수로 자기 권한을 강등하면 시스템에서 모든 관리자가 사라질 위험이 있다.
        // 권한 회복을 위해 결국 DB 에 직접 SQL 을 쳐야 하는 상황이 벌어진다.
        if (requesterId.equals(targetId)) {
            throw new IllegalArgumentException("자기 자신의 권한은 변경할 수 없습니다.");
        }

        // 가드 3 — role 값 화이트리스트.
        // 임의의 문자열이 들어오면 권한 시스템 전체가 의도치 않은 상태가 된다.
        // Enum 으로 받는 방법도 있지만, JWT 의 role claim 이 String 이라서 일관성을 위해 String 검증을 택했다.
        if (!"ROLE_ADMIN".equals(newRole) && !"ROLE_USER".equals(newRole)) {
            throw new IllegalArgumentException("유효하지 않은 권한입니다: " + newRole);
        }

        // 대상 조회. 이 시점부터 account 는 영속 상태이므로 changeRole 호출 후 save() 가 필요 없다.
        Account account = findAccountOrThrow(targetId);

        // 도메인 메서드 호출 — Account 엔티티가 자기 상태를 스스로 바꾼다.
        account.changeRole(newRole);

        // 응답 DTO 변환 후 반환. 트랜잭션 종료 시점(이 메서드 return 직후)에 UPDATE 가 실제로 발행된다.
        return AccountSummaryResponse.from(account);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // private 헬퍼
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * id 로 계정을 조회하고 없으면 IllegalArgumentException 을 던지는 공통 로직.
     * <p>
     * 이 헬퍼를 두는 이유: 조회 메서드 4개 + updateRole 까지 동일한 패턴이 5번 반복되기 때문이다.
     * 한 곳에 모아두면 예외 메시지 형식이 자동으로 일관되고, 추후 "회원을 찾지 못함" 을
     * 별도의 예외 타입(예: AccountNotFoundException) 으로 분리하고 싶을 때
     * 이 한 줄만 바꾸면 된다.
     */
    private Account findAccountOrThrow(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("계정을 찾을 수 없습니다: id=" + id));
    }
}