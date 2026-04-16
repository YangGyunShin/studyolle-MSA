package com.studyolle.account.service;

import com.studyolle.account.dto.response.AccountSummaryResponse;
import com.studyolle.account.entity.Account;
import com.studyolle.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내부 전용 (/internal/**) API 가 필요로 하는 비즈니스 로직을 담는 service.
 *
 * [왜 새로 만들었는가]
 * 기존 AccountInternalController 는 단순 조회만 했기에 service 계층 없이
 * Repository 를 직접 호출했다. 그러나 권한 변경(updateRole) 은 다음 세 가지
 * 비즈니스 규칙이 한 메서드 안에서 검증·실행되어야 한다:
 *
 *   1) 요청자 정보 존재 여부 (X-Account-Id 헤더 필수)
 *   2) 자기 자신 권한 변경 금지
 *   3) ROLE_ADMIN / ROLE_USER 화이트리스트 검증
 *
 * 이런 검증 로직을 controller 에 두면 컨트롤러가 비대해지고 단위 테스트도 까다로워진다.
 * 그래서 이 service 가 만들어졌다.
 *
 * [현재 상태]
 * 권한 변경(updateRole) 만 이 service 에 있다.
 * AccountInternalController 에 남아 있는 조회 API 들은 추후 모두 이 service 로 옮길 예정이다.
 */
@Service
@RequiredArgsConstructor
public class AccountInternalService {

    private final AccountRepository accountRepository;

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
        Account account = accountRepository.findById(targetId)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다: id=" + targetId));

        // 도메인 메서드 호출 — Account 엔티티가 자기 상태를 스스로 바꾼다.
        account.changeRole(newRole);

        // 응답 DTO 변환 후 반환. 트랜잭션 종료 시점(이 메서드 return 직후)에 UPDATE 가 실제로 발행된다.
        return AccountSummaryResponse.from(account);
    }
}