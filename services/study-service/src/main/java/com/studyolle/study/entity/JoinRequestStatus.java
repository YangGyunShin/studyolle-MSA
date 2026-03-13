package com.studyolle.study.entity;

// 스터디 가입 신청의 처리 상태를 나타내는 열거형.
// JoinRequest.status 필드에 저장되며, 신청 생성 시 PENDING 으로 시작해
// 관리자 처리에 따라 APPROVED 또는 REJECTED 로 전이된다.
public enum JoinRequestStatus {

    // 신청 접수 후 관리자 처리를 기다리는 상태.
    // 이 상태의 신청만 승인/거절 처리가 가능하다.
    PENDING,

    // 관리자가 승인한 상태.
    // 승인 시 Study.addMember() 가 함께 호출되어 memberIds 에 추가된다.
    APPROVED,

    // 관리자가 거절한 상태.
    // memberIds 에는 추가되지 않는다.
    REJECTED
}

/*
 * [상태 전이(State Machine) 패턴]
 *
 * 가입 신청은 아래 방향으로만 상태가 바뀐다:
 *
 *   PENDING → APPROVED
 *   PENDING → REJECTED
 *
 * APPROVED 나 REJECTED 에서 다른 상태로 되돌아가는 것은 허용하지 않는다.
 * JoinRequest.approve(), JoinRequest.reject() 에서 isPending() 으로 먼저 확인한 뒤
 * 상태를 변경하므로 이미 처리된 신청을 중복 처리하는 것을 방지한다.
 *
 *
 * [왜 boolean approved 하나가 아닌 3가지 상태를 쓰는가?]
 *
 * boolean 으로 승인 여부만 표현하면 "처리 전(PENDING)" 과 "거절됨(REJECTED)" 을
 * 구분할 수 없다. 둘 다 false 가 되기 때문이다.
 * 3가지 상태를 두면 "아직 처리 안 됨", "승인됨", "거절됨" 을 명확히 구분할 수 있다.
 */
