package com.studyolle.notification.repository;

import com.studyolle.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 알림 데이터 접근 레이어.
 *
 * JpaRepository<Notification, Long> 을 상속하면
 * save(), findById(), delete() 등 기본 CRUD 메서드를 자동으로 제공한다.
 *
 * 커스텀 메서드 2개를 추가로 선언한다:
 *   1. findByAccountIdAndCheckedFalseOrderByCreatedAtDesc : 읽지 않은 알림 목록 조회
 *   2. markAllAsRead : 전체 읽음 처리 (벌크 UPDATE)
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * 특정 사용자의 읽지 않은 알림 목록을 최신순으로 조회한다.
     *
     * 메서드 이름으로 쿼리를 자동 생성하는 Spring Data JPA 의 쿼리 메서드 방식이다.
     * 이름 규칙:
     *   findBy        → SELECT ... WHERE
     *   AccountId     → account_id = :accountId
     *   AndCheckedFalse → AND checked = false
     *   OrderByCreatedAtDesc → ORDER BY created_at DESC (최신순)
     *
     * 생성되는 SQL:
     *   SELECT * FROM notifications
     *   WHERE account_id = ? AND checked = false
     *   ORDER BY created_at DESC
     */
    List<Notification> findByAccountIdAndCheckedFalseOrderByCreatedAtDesc(Long accountId);

    /**
     * 특정 사용자의 읽지 않은 알림을 전체 읽음 처리한다 (벌크 UPDATE).
     *
     * @Modifying : INSERT / UPDATE / DELETE 쿼리임을 Spring Data JPA 에 알린다.
     *              이 어노테이션 없이 @Query 로 UPDATE 를 실행하면 예외가 발생한다.
     *
     * @Transactional : 벌크 UPDATE 는 자체 트랜잭션이 필요하다.
     *                  NotificationService 의 @Transactional 트랜잭션 안에서 호출되더라도
     *                  @Modifying 메서드는 명시적 @Transactional 이 있어야 한다.
     *                  없으면 "No EntityManager with actual transaction available" 에러 발생.
     *
     * @Query : JPQL 쿼리를 직접 작성한다.
     *          메서드 이름으로 표현하기 어려운 복잡한 쿼리에 사용한다.
     *          UPDATE 는 메서드 이름으로 자동 생성이 불가능하므로 @Query 가 필수다.
     *
     *          :accountId 는 메서드 파라미터 Long accountId 에 자동 바인딩된다.
     *
     * 생성되는 SQL:
     *   UPDATE notifications
     *   SET checked = true
     *   WHERE account_id = ? AND checked = false
     */
    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.checked = true WHERE n.accountId = :accountId AND n.checked = false")
    void markAllAsRead(Long accountId);
}
