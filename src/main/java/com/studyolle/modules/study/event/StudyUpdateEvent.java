package com.studyolle.modules.study.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * StudyUpdateEvent - 스터디 정보 변경 시 발행되는 도메인 이벤트
 *
 * =============================================
 * 발행 시점
 * =============================================
 *
 * 이미 공개된 스터디의 상태나 정보가 변경될 때 발행됩니다:
 * - 스터디 종료 (close): "스터디를 종료했습니다."
 * - 팀원 모집 시작 (startRecruit): "팀원 모집을 시작합니다."
 * - 팀원 모집 중단 (stopRecruit): "팀원 모집을 중단했습니다."
 * - 소개 수정 (updateStudyDescription): "스터디 소개를 수정했습니다."
 *
 * =============================================
 * 수신자 및 처리 로직
 * =============================================
 *
 * StudyEventListener.handleStudyUpdateEvent()가 이 이벤트를 감지합니다.
 *
 * 알림 대상:
 * - 해당 스터디의 관리자(managers) + 멤버(members) 전원
 * - StudyCreatedEvent와 달리, 이미 소속된 사용자에게만 알림을 보냅니다
 *
 * 알림 방식:
 * - 이메일 알림: account.isStudyUpdatedByEmail()이 true인 경우
 * - 웹 알림: account.isStudyUpdatedByWeb()이 true인 경우
 *
 * =============================================
 * message 필드의 역할
 * =============================================
 *
 * StudyCreatedEvent와 달리 message 필드를 추가로 가지고 있습니다.
 * 이는 어떤 변경이 발생했는지를 사용자에게 구체적으로 안내하기 위함입니다.
 * 이메일 본문과 웹 알림 메시지에 모두 사용됩니다.
 */
@Getter
@RequiredArgsConstructor
public class StudyUpdateEvent {

    /** 변경된 스터디 객체. 이벤트 리스너에서 ID를 통해 상세 정보를 다시 조회합니다. */
    private final Study study;

    /** 사용자에게 전달될 변경 내용 설명 메시지 (예: "스터디를 종료했습니다.") */
    private final String message;
}