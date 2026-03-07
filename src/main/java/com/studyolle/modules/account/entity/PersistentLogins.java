package com.studyolle.modules.account.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Remember-Me(자동 로그인) 토큰을 저장하는 persistent_logins 테이블의 엔티티
 *
 * ⚠️ 이 엔티티의 역할:
 *   - Hibernate(JPA)가 앱 시작 시 persistent_logins 테이블을 "자동 생성"하기 위해 존재
 *   - ddl-auto=create/update 설정이 있으면, 이 @Entity를 보고 테이블을 만듦
 *   - 실제 토큰의 읽기/쓰기/갱신은 JdbcTokenRepositoryImpl이 직접 SQL로 처리
 *   - 즉, 이 클래스는 "테이블 생성용"이지, Remember-Me 로직에서 직접 사용되지는 않음
 *
 * ============================================================
 *  📌 왜 series와 token을 분리했는가? (토큰 도난 감지 설계)
 * ============================================================
 *
 *  [정상적인 자동 로그인 흐름]
 *    1. 최초 로그인    → series=AAA, token=111 생성 → 쿠키 + DB에 저장
 *    2. 재방문 시      → 쿠키(AAA, 111) → DB 일치 → 성공 → token을 222로 갱신
 *    3. 또 재방문 시   → 쿠키(AAA, 222) → DB 일치 → 성공 → token을 333으로 갱신
 *    → series는 항상 고정, token만 매번 바뀜
 *
 *  [해커가 쿠키를 탈취한 경우]
 *    1. 현재 상태: 내 쿠키(AAA, 222), DB(AAA, 222)
 *    2. 해커가 쿠키(AAA, 222) 탈취 후 먼저 접속 → 성공 → DB token이 333으로 갱신
 *    3. 내가 접속 → 쿠키(AAA, 222) 전송 → DB에는 333 → token 불일치!
 *    4. → "series는 같은데 token이 다르다" = 도난으로 판단
 *    5. → 해당 series 삭제 → 해커/나 모두 자동 로그인 차단 → 재로그인 요구
 *
 *  [만약 토큰 하나만 사용했다면?]
 *    → 해커 로그인 = 정상 로그인과 구분 불가 → 도난 감지 불가능
 */
@Table(name = "persistent_logins")  // 테이블 이름 — Spring Security 규약이므로 변경 불가
@Entity
@Getter
@Setter
public class PersistentLogins {

    /**
     * 시리즈 식별자 (Primary Key) — 고정값
     *
     * - 최초 "로그인 유지" 체크 시 생성, 이후 자동 로그인을 반복해도 변하지 않음
     * - "이 쿠키가 어떤 로그인 세션에서 발급되었는지"를 식별하는 역할
     *
     * 💡 도난 감지: series가 같은데 token이 다르면 → 누군가 먼저 내 쿠키로 로그인한 것
     */
    @Id
    @Column(length = 64)
    private String series;

    /**
     * 사용자 식별자 (username)
     * - 로그인한 사용자의 이메일 또는 닉네임
     */
    @Column(nullable = false, length = 64)
    private String username;

    /**
     * 인증 토큰 — 매번 갱신됨
     *
     * - 자동 로그인에 성공할 때마다 새 값으로 교체 (series는 유지)
     *
     * 💡 도난 감지: 해커가 먼저 로그인 → 이 값이 갱신됨
     *    → 진짜 사용자의 쿠키에는 이전 token이 남아 있으므로 불일치 발생
     */
    @Column(nullable = false, length = 64)
    private String token;

    /**
     * 마지막 사용 시각 (토큰 갱신 시각)
     * - 자동 로그인 시마다 갱신, 오래된 토큰 만료 판단에 사용
     */
    @Column(name = "last_used", nullable = false, length = 64)
    private LocalDateTime lastUsed;
}