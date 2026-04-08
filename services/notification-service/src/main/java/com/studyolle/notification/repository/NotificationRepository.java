package com.studyolle.notification.repository;

import com.studyolle.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // 읽지 않은 알림 목록 최신순 조회
    List<Notification> findByAccountIdAndCheckedFalseOrderByCreatedAtDesc(Long accountId);

    // 전체 읽음 처리 (벌크 UPDATE)
    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.checked = true WHERE n.accountId = :accountId AND n.checked = false")
    void markAllAsRead(Long accountId);
}