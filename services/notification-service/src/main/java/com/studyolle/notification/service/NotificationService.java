package com.studyolle.notification.service;

import com.studyolle.notification.entity.Notification;
import com.studyolle.notification.entity.NotificationType;
import com.studyolle.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final RedisTemplate<String, String> redisTemplate;

    // Redis Key 접두사
    private static final String UNREAD_KEY = "notification:unread:";  // 읽지 않은 알림 카운터
    private static final String DEDUP_KEY  = "notification:dedup:";   // 중복 처리 방지

    /**
     * 알림을 생성하고 Redis 카운터를 증가시킨다.
     *
     * Kafka / RabbitMQ Consumer 에서 호출한다.
     * PostgreSQL 에 알림을 영구 저장하고, Redis 카운터를 +1 한다.
     */
    public void createNotification(Long accountId, String message, String link, NotificationType type) {
        notificationRepository.save(Notification.builder()
                .accountId(accountId)
                .message(message)
                .link(link)
                .type(type)
                .checked(false)
                .createdAt(LocalDateTime.now())
                .build());

        // Redis 카운터 +1 (nav 바 뱃지용)
        redisTemplate.opsForValue().increment(UNREAD_KEY + accountId);
        log.info("[Notification] 생성: accountId={}, type={}", accountId, type);
    }

    /**
     * 중복 이벤트 처리 방지.
     *
     * SETNX(Set if Not eXists) 로 키가 없을 때만 저장한다.
     * true  = 처음 처리 → 알림 생성 진행
     * false = 이미 처리된 이벤트 → 무시
     *
     * TTL 1일: 하루가 지나면 키가 자동 삭제되어 같은 이벤트를 재처리할 수 있다.
     */
    public boolean isFirstProcessing(String eventKey) {
        Boolean isNew = redisTemplate.opsForValue()
                .setIfAbsent(DEDUP_KEY + eventKey, "1", Duration.ofDays(1));
        return Boolean.TRUE.equals(isNew);
    }

    /**
     * 읽지 않은 알림 수를 Redis 에서 즉시 반환한다.
     *
     * DB 조회 없이 Redis 에서 바로 읽으므로 nav 바 뱃지 표시에 적합하다.
     */
    @Transactional(readOnly = true)
    public long getUnreadCount(Long accountId) {
        String value = redisTemplate.opsForValue().get(UNREAD_KEY + accountId);
        return value == null ? 0L : Long.parseLong(value);
    }

    /**
     * 읽지 않은 알림 목록을 PostgreSQL 에서 조회한다.
     */
    @Transactional(readOnly = true)
    public List<Notification> getUnreadNotifications(Long accountId) {
        return notificationRepository
                .findByAccountIdAndCheckedFalseOrderByCreatedAtDesc(accountId);
    }

    /**
     * 특정 사용자의 전체 알림 목록을 최신순으로 조회한다.
     *
     * getUnreadNotifications() 는 읽지 않은 알림만 반환하지만,
     * 이 메서드는 읽은 알림까지 모두 반환한다.
     *
     * 프론트엔드에서 읽음/안 읽음을 시각적으로 구분하여 표시할 때 사용한다.
     *
     * @param accountId 조회 대상 사용자 ID
     * @return 전체 알림 목록 (최신순)
     */
    public List<Notification> getAllNotifications(Long accountId) {
        return notificationRepository.findAllByAccountIdOrderByCreatedAtDesc(accountId);
    }

    /**
     * 전체 읽음 처리: PostgreSQL 벌크 UPDATE + Redis 카운터 0으로 초기화.
     */
    public void markAllAsRead(Long accountId) {
        notificationRepository.markAllAsRead(accountId);
        redisTemplate.opsForValue().set(UNREAD_KEY + accountId, "0");
    }

    /**
     * 단건 읽음 처리: PostgreSQL checked = true + Redis 카운터 -1.
     */
    public void markAsRead(Long notificationId, Long accountId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "알림을 찾을 수 없습니다. id=" + notificationId));

        if (!notification.isChecked()) {
            notification.setChecked(true);
            // Dirty Checking 으로 자동 UPDATE

            Long count = redisTemplate.opsForValue().decrement(UNREAD_KEY + accountId);
            // 0 아래로 내려가지 않도록 보정
            if (count != null && count < 0) {
                redisTemplate.opsForValue().set(UNREAD_KEY + accountId, "0");
            }
        }
    }
}