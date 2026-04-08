package com.studyolle.notification.dto;

import com.studyolle.notification.entity.Notification;
import com.studyolle.notification.entity.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {

    private Long id;
    private String message;
    private String link;
    private NotificationType type;
    private boolean checked;
    private LocalDateTime createdAt;

    public static NotificationResponse from(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .message(n.getMessage())
                .link(n.getLink())
                .type(n.getType())
                .checked(n.isChecked())
                .createdAt(n.getCreatedAt())
                .build();
    }
}