package com.studyolle.notification.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long accountId;        // 알림 수신 대상 사용자

    @Column(nullable = false)
    private String message;        // 알림 내용

    @Column(nullable = false)
    private String link;           // 클릭 시 이동할 경로

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type; // STUDY / ENROLLMENT

    @Column(nullable = false)
    private boolean checked;       // 읽음 여부

    @Column(nullable = false)
    private LocalDateTime createdAt;
}