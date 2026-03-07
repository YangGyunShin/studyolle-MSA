package com.studyolle.modules.notification;

import com.studyolle.modules.account.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Notification 엔티티 전용 JPA Repository
 *
 * - Spring Data JPA의 쿼리 메서드 네이밍 규칙을 활용한 자동 쿼리 생성
 * - 읽음/안읽음 상태를 기준으로 조회, 카운트, 삭제 메서드를 제공
 *
 * =====================================================
 * [인터페이스 레벨 @Transactional(readOnly = true)]
 *
 * 이 인터페이스의 모든 메서드에 기본으로 읽기 전용 트랜잭션이 적용됨.
 * 읽기 전용 트랜잭션의 이점:
 *   - JPA 더티 체킹(변경 감지) 생략 -> 성능 향상
 *   - DB 레벨에서 읽기 최적화 가능 (예: MySQL의 읽기 전용 힌트)
 *   - 실수로 엔티티를 수정해도 DB에 반영되지 않아 안전
 *
 * 쓰기가 필요한 메서드(delete 등)에는 개별 @Transactional을 붙여
 * readOnly=false(기본값)로 오버라이드함.
 */
@Transactional(readOnly = true)
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * 특정 사용자의 읽음/안읽음 알림 개수 카운트
     *
     * - 인터셉터(NotificationInterceptor)에서 읽지 않은 알림 여부 확인에 사용
     * - 알림 목록 화면에서 읽음/안읽음 개수 표시에 사용
     *
     * 생성되는 쿼리:
     *   SELECT COUNT(n) FROM Notification n
     *   WHERE n.account = :account AND n.checked = :checked
     */
    long countByAccountAndChecked(Account account, boolean checked);

    /**
     * 특정 사용자의 알림 목록 조회 (읽음 여부에 따라 구분, 최신순 정렬)
     *
     * - OrderByCreatedDateTimeDesc: 생성일시 내림차순 -> 최신 알림이 먼저
     * - 인터페이스 레벨 @Transactional(readOnly = true)이 적용됨
     *
     * 생성되는 쿼리:
     *   SELECT n FROM Notification n
     *   WHERE n.account = :account AND n.checked = :checked
     *   ORDER BY n.createdDateTime DESC
     */
    List<Notification> findByAccountAndCheckedOrderByCreatedDateTimeDesc(Account account, boolean checked);

    /**
     * 특정 사용자의 읽은 알림 전체 삭제
     *
     * - @Transactional: 쓰기 작업이므로 인터페이스의 readOnly=true를 오버라이드
     * - Spring Data JPA의 deleteBy 패턴: 조건에 맞는 엔티티를 먼저 조회 후 삭제
     *   (벌크 DELETE가 아닌, 개별 엔티티 삭제 방식으로 동작)
     *
     * [참고] 대량 삭제 시에는 @Modifying + @Query로 벌크 DELETE 쿼리를
     *        직접 작성하는 것이 성능상 유리함
     */
    @Transactional
    void deleteByAccountAndChecked(Account account, boolean checked);
}