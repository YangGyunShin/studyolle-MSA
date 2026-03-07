package com.studyolle.modules.account.service;

import com.studyolle.modules.account.entity.Account;
import com.studyolle.modules.account.repository.AccountRepository;
import com.studyolle.modules.tag.Tag;
import com.studyolle.modules.zone.Zone;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;

/**
 * ✅ 사용자의 관심 태그 및 활동 지역 관리를 담당하는 서비스
 *
 * 담당 기능:
 *   - 태그 조회/추가/삭제 (getTags, addTag, removeTag)
 *   - 지역 조회/추가/삭제 (getZones, addZone, removeZone)
 *
 * 설계 의도:
 *   - 태그와 지역은 Account ↔ Tag/Zone @ManyToMany 연관관계를 조작하는 동일한 패턴
 *   - 기존 AccountService에서 TagRepository, ZoneRepository에 직접 의존하던 부분을 분리
 *   - TagZoneController와 1:1로 대응
 *
 * Detached 상태 처리:
 *   - @CurrentUser로 주입받는 Account는 SecurityContext에서 가져온 Detached 상태
 *   - Detached 상태에서는 Lazy Loading이 불가능하고, 연관관계 변경도 추적되지 않음
 *   - 따라서 모든 메서드에서 accountRepository.findById()로 영속 상태로 재조회한 후 작업
 *
 * 영속 상태에서의 연관관계 조작:
 *   - a.getTags().add(tag) → JPA가 Dirty Checking으로 조인 테이블에 INSERT
 *   - a.getTags().remove(tag) → JPA가 Dirty Checking으로 조인 테이블에서 DELETE
 *   - 트랜잭션 커밋(flush) 시점에 쿼리가 자동 실행됨
 *   - 별도의 save() 호출이 필요 없음 (영속 상태이므로 Dirty Checking이 작동)
 *
 * Detached vs 영속 상태의 차이:
 *   - AccountSettingsService에서는 save()를 명시적으로 호출 (Detached → merge)
 *   - 이 서비스에서는 findById()로 재조회 후 컬렉션 조작 (영속 상태 → Dirty Checking)
 *   - 두 방식 모두 DB에 반영되지만, 접근 경로가 다름
 */
@Service
@Transactional
@RequiredArgsConstructor
public class TagZoneService {

    private final AccountRepository accountRepository;

    // ──────────────────────────────────────────
    // 태그 (Tag) 관련
    // ──────────────────────────────────────────

    /**
     * ✅ 현재 사용자의 관심 태그 목록 조회
     *
     * - Detached 상태의 account를 ID로 재조회하여 영속 상태로 전환
     * - 영속 상태에서 getTags() 호출 시 Lazy Loading으로 연관 태그를 조회
     *   → SELECT t.* FROM tag t JOIN account_tags at ON t.id = at.tag_id WHERE at.account_id = ?
     * - @Transactional 범위 내에서만 Lazy Loading이 정상 작동
     *
     * @param account 현재 로그인한 사용자 (Detached 상태)
     * @return 사용자에게 연결된 Tag 집합 (Set<Tag>)
     */
    public Set<Tag> getTags(Account account) {
        Optional<Account> byId = accountRepository.findById(account.getId());
        return byId.orElseThrow().getTags();
    }

    /**
     * ✅ 사용자에게 관심 태그 추가
     *
     * - account를 영속 상태로 재조회한 후 연관 컬렉션에 태그 추가
     * - JPA가 변경을 감지하여 트랜잭션 커밋 시 조인 테이블에 INSERT 실행
     *   → INSERT INTO account_tags (account_id, tag_id) VALUES (?, ?)
     * - Set<Tag> 사용으로 동일 태그 중복 추가 방지 (equals/hashCode 기반)
     * - tag는 반드시 이미 DB에 저장된(persisted) 상태여야 함
     *   (CascadeType.PERSIST가 설정되어 있지 않으므로)
     *
     * @param account 현재 로그인한 사용자 (Detached 상태)
     * @param tag     추가할 태그 (영속 상태)
     */
    public void addTag(Account account, Tag tag) {
        Optional<Account> byId = accountRepository.findById(account.getId());
        byId.ifPresent(a -> a.getTags().add(tag));
    }

    /**
     * ✅ 사용자의 관심 태그 삭제
     *
     * - account를 영속 상태로 재조회한 후 연관 컬렉션에서 태그 제거
     * - JPA가 변경을 감지하여 트랜잭션 커밋 시 조인 테이블에서 DELETE 실행
     *   → DELETE FROM account_tags WHERE account_id = ? AND tag_id = ?
     * - remove()가 정상 작동하려면 Tag의 equals/hashCode가 id 기준으로 정의되어 있어야 함
     * - 태그 엔티티 자체는 삭제하지 않음 (다른 사용자/스터디에서 공유 사용 가능)
     *
     * @param account 현재 로그인한 사용자 (Detached 상태)
     * @param tag     삭제할 태그 (영속 상태)
     */
    public void removeTag(Account account, Tag tag) {
        Optional<Account> byId = accountRepository.findById(account.getId());
        byId.ifPresent(a -> a.getTags().remove(tag));
    }

    // ──────────────────────────────────────────
    // 지역 (Zone) 관련
    // ──────────────────────────────────────────

    /**
     * ✅ 현재 사용자의 활동 지역 목록 조회
     *
     * - getTags()와 동일한 패턴
     * - 영속 상태로 재조회 후 Lazy Loading으로 zones 조회
     *
     * @param account 현재 로그인한 사용자 (Detached 상태)
     * @return 사용자에게 연결된 Zone 집합 (Set<Zone>)
     */
    public Set<Zone> getZones(Account account) {
        Optional<Account> byId = accountRepository.findById(account.getId());
        return byId.orElseThrow().getZones();
    }

    /**
     * ✅ 사용자에게 활동 지역 추가
     *
     * - addTag()와 동일한 패턴
     * - zone은 미리 DB에 등록된 데이터만 사용 (태그와 달리 새로 생성하지 않음)
     *   → INSERT INTO account_zones (account_id, zone_id) VALUES (?, ?)
     *
     * @param account 현재 로그인한 사용자 (Detached 상태)
     * @param zone    추가할 지역 (영속 상태)
     */
    public void addZone(Account account, Zone zone) {
        Optional<Account> byId = accountRepository.findById(account.getId());
        byId.ifPresent(a -> a.getZones().add(zone));
    }

    /**
     * ✅ 사용자의 활동 지역 삭제
     *
     * - removeTag()와 동일한 패턴
     * - Zone 엔티티 자체는 삭제하지 않음 (시스템 공유 데이터)
     *   → DELETE FROM account_zones WHERE account_id = ? AND zone_id = ?
     *
     * @param account 현재 로그인한 사용자 (Detached 상태)
     * @param zone    삭제할 지역 (영속 상태)
     */
    public void removeZone(Account account, Zone zone) {
        Optional<Account> byId = accountRepository.findById(account.getId());
        byId.ifPresent(a -> a.getZones().remove(zone));
    }
}

/**
 * ✅ 사용자의 관심 태그 및 활동 지역 관리를 담당하는 서비스
 *
 * 담당 기능:
 *   - 태그 조회/추가/삭제 (getTags, addTag, removeTag)
 *   - 지역 조회/추가/삭제 (getZones, addZone, removeZone)
 *
 * ──────────────────────────────────────────────────────────────────
 * [핵심 설계 포인트] findById() 재조회가 필수인 이유 — Lazy Loading 제약
 * ──────────────────────────────────────────────────────────────────
 *
 * 이 서비스의 모든 메서드는 다음 패턴을 따른다:
 *
 *   Optional<Account> byId = accountRepository.findById(account.getId());
 *   byId.ifPresent(a -> a.getTags().add(tag));   // save() 호출 없음
 *
 * 왜 findById()로 재조회하는가?
 *   - @CurrentUser로 주입받는 Account는 SecurityContext(세션)에서 꺼낸 Detached 상태 객체
 *   - tags, zones 필드는 @ManyToMany(fetch = LAZY) 설정이므로,
 *     Detached 상태에서 getTags()/getZones()를 호출하면 DB 연결이 없어
 *     LazyInitializationException이 발생한다
 *   - 따라서 findById()로 영속 상태로 재조회해야만 Lazy Loading이 정상 작동한다
 *
 * 왜 save()가 필요 없는가?
 *   - findById()로 조회된 객체는 영속성 컨텍스트가 관리하는 영속 상태
 *   - 영속 상태에서는 JPA의 Dirty Checking이 자동으로 작동
 *   - a.getTags().add(tag) 같은 컬렉션 변경을 JPA가 감지하여
 *     트랜잭션 커밋(flush) 시점에 INSERT/DELETE 쿼리를 자동 실행
 *   - 즉, findById()는 "영속 상태를 만들기 위해" 호출한 것이 아니라
 *     "Lazy 컬렉션에 접근하기 위해" 강제된 것이고,
 *     Dirty Checking은 그에 따른 자연스러운 부산물이다
 *
 * AccountSettingsService와의 차이:
 *   - AccountSettingsService는 setPassword(), setNickname() 같은 단순 스칼라 필드만 수정
 *   - 단순 필드는 Lazy Loading이 필요 없으므로 Detached 상태에서도 수정 가능
 *   - 대신 Dirty Checking이 작동하지 않으므로 save()를 명시적으로 호출하여 merge 유발
 *   - 불필요한 SELECT 쿼리(findById)를 아끼는 더 효율적인 방식
 *
 *   ┌──────────────────────┬─────────────────────┬──────────────────────────┐
 *   │                      │   TagZoneService    │  AccountSettingsService  │
 *   ├──────────────────────┼─────────────────────┼──────────────────────────┤
 *   │ 접근 대상              │ Lazy 컬렉션           │ 단순 스칼라 필드             │
 *   │                      │ (getTags, getZones) │ (password, nickname 등)   │
 *   ├──────────────────────┼─────────────────────┼──────────────────────────┤
 *   │ findById() 필요 여부   │ 필수 (Lazy Loading)  │ 불필요                     │
 *   ├──────────────────────┼─────────────────────┼──────────────────────────┤
 *   │ DB 반영 방식           │ Dirty Checking       │ save() -> merge         │
 *   │                      │ (자동)               │ (명시적 호출)               │
 *   ├──────────────────────┼─────────────────────┼──────────────────────────┤
 *   │ SELECT 쿼리           │ 1회 (findById)      │ 0회                       │
 *   ├──────────────────────┼─────────────────────┼──────────────────────────┤
 *   │ UPDATE/INSERT 쿼리    │ flush 시 자동 실행     │ save() 시 merge로 실행     │
 *   └──────────────────────┴─────────────────────┴──────────────────────────┘
 *
 * 호출 관계:
 *   - TagZoneController → TagZoneService (태그/지역 CRUD)
 */