// ✅ AccountRepository.java: 사용자 정보에 접근하는 JPA 인터페이스
package com.studyolle.account.repository;

import com.studyolle.account.entity.Account;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public interface AccountRepository extends JpaRepository<Account, Long> {

    // 이메일 중복 여부 체크
    boolean existsByEmail(String email);

    // 닉네임 중복 여부 체크
    boolean existsByNickname(String nickname);

    // 이메일 기반 사용자 조회
    Account findByEmail(String mail);

    // 닉네임 기반 사용자 조회
    Account findByNickname(String nickname);

    Page<Account> findByEmailContainingOrNicknameContaining(String email, String nickname, Pageable pageable);
}