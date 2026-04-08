package com.studyolle.notification.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * study-service 가 Kafka 로 발행하는 스터디 이벤트 DTO (Consumer 측 역직렬화용).
 *
 * =============================================
 * 중요: study-service 의 StudyEventDto 와 필드가 동일해야 한다
 * =============================================
 *
 * Kafka 는 메시지를 JSON 문자열로 전송한다.
 * Consumer 측(notification-service)에서 JSON 을 이 클래스로 역직렬화할 때
 * 필드 이름이 다르면 해당 필드는 null 로 채워진다.
 *
 * 필드 이름, 타입을 study-service 의 StudyEventDto 와 반드시 동일하게 유지해야 한다.
 * 두 서비스가 공유 라이브러리 없이 독립적으로 같은 구조를 선언하는 것이
 * MSA 에서 일반적인 방식이다 (Shared Kernel 패턴을 피하기 위함).
 *
 * =============================================
 * 발행되는 이벤트 종류 (eventType 값)
 * =============================================
 *
 * eventType            발행 시점
 * STUDY_CREATED        스터디 생성 시 (StudyService.createNewStudy)
 * STUDY_PUBLISHED      스터디 공개 시 (StudySettingsService.publish)
 * RECRUITING_STARTED   모집 시작 시  (StudySettingsService.startRecruit)
 * RECRUITING_STOPPED   모집 종료 시  (StudySettingsService.stopRecruit)
 *
 * =============================================
 * studyPath 가 Kafka Key 인 이유
 * =============================================
 *
 * study-service 는 메시지 발행 시 studyPath 를 Kafka 메시지 Key 로 사용한다.
 * 같은 Key 를 가진 메시지는 항상 같은 Partition 으로 라우팅된다.
 * 하나의 Partition 안에서는 메시지 순서가 보장되므로
 * 같은 스터디의 이벤트는 발행 순서대로 처리된다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudyEventDto {

    /**
     * 이벤트 종류.
     * STUDY_CREATED / STUDY_PUBLISHED / RECRUITING_STARTED / RECRUITING_STOPPED
     * StudyEventConsumer 에서 switch 문으로 분기 처리한다.
     */
    private String eventType;

    /** 스터디 경로. Kafka 메시지 Key 로 사용되며, 알림 링크 생성에도 사용된다. */
    private String studyPath;

    /** 스터디 제목. 알림 메시지 내용에 포함된다. */
    private String studyTitle;

    /**
     * 이벤트를 발생시킨 사용자 ID.
     * 이 사용자에게 알림을 보낸다.
     * StudySettingsService.publish() 등에서 accountId 를 전달한다.
     */
    private Long triggeredByAccountId;

    /**
     * 이벤트 발생 시각.
     * 중복 방지 키 생성에 사용된다: "study:{studyPath}:{occurredAt}"
     */
    private LocalDateTime occurredAt;
}
