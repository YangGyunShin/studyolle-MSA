package com.studyolle.modules.study.event;

import com.studyolle.modules.study.entity.Study;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * StudyCreatedEvent - 스터디 최초 공개 시 발행되는 도메인 이벤트
 *
 * =============================================
 * 발행 시점
 * =============================================
 *
 * StudySettingsService.publish() 메서드 내에서 study.publish() 호출 직후 발행됩니다.
 * "스터디 생성" 시점이 아니라 "스터디 공개" 시점에 발행된다는 점이 중요합니다.
 * 아직 공개되지 않은 스터디는 외부 사용자에게 알릴 필요가 없기 때문입니다.
 *
 * =============================================
 * 수신자 및 처리 로직
 * =============================================
 *
 * StudyEventListener.handleStudyCreatedEvent()가 이 이벤트를 감지합니다.
 *
 * 알림 대상 선정:
 * - 스터디의 tags/zones와 사용자의 관심 tags/zones가 일치하는 모든 사용자
 * - AccountPredicates.findByTagsAndZones()를 통해 QueryDSL로 조회
 *
 * 알림 방식:
 * - 이메일 알림: account.isStudyCreatedByEmail()이 true인 경우
 * - 웹 알림: account.isStudyCreatedByWeb()이 true인 경우
 *
 * =============================================
 * StudyUpdateEvent와의 차이
 * =============================================
 *
 * - StudyCreatedEvent: 관심사 매칭 기반으로 "외부 잠재적 멤버"에게 알림
 * - StudyUpdateEvent: "이미 소속된 관리자+멤버"에게 알림
 *
 * 두 이벤트는 알림 대상 범위가 근본적으로 다르기 때문에 별도 클래스로 분리되어 있습니다.
 */
@Getter
@RequiredArgsConstructor
public class StudyCreatedEvent {

    /** 공개된 스터디 객체. 이벤트 리스너에서 ID를 통해 상세 정보를 다시 조회합니다. */
    private final Study study;
}