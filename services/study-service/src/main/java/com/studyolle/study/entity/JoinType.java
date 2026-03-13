package com.studyolle.study.entity;

// 스터디 가입 방식을 나타내는 열거형.
// Study.joinType 필드에 저장되며, StudyController.joinStudy() 에서
// 이 값을 읽어 즉시 가입(OPEN) 또는 신청 접수(APPROVAL_REQUIRED) 로 분기한다.
public enum JoinType {

    // 자유 가입. 신청 즉시 memberIds 에 추가된다.
    // JoinRequest 가 생성되지 않으므로 관리자 승인 절차가 없다.
    OPEN,

    // 승인제 가입. 신청 시 JoinRequest(PENDING) 가 생성되고,
    // 관리자가 승인해야 비로소 memberIds 에 추가된다.
    APPROVAL_REQUIRED
}

/*
 * [enum 을 쓰는 이유]
 *
 * 가입 방식을 "OPEN", "APPROVAL_REQUIRED" 같은 문자열이나 0, 1 같은 숫자로 저장하면
 * 오타가 발생해도 컴파일 오류가 나지 않고, 허용되지 않은 값이 DB 에 저장될 수 있다.
 * enum 은 정의된 값만 사용할 수 있으므로 컴파일 타임에 안전하다.
 *
 * Study 엔티티에서 @Enumerated(EnumType.STRING) 으로 선언하면
 * DB 에 "OPEN", "APPROVAL_REQUIRED" 라는 문자열로 저장된다.
 * EnumType.ORDINAL(기본값) 을 쓰면 순서 번호(0, 1)로 저장되는데,
 * 나중에 enum 에 값을 추가하거나 순서를 바꾸면 기존 데이터가 잘못 해석되는 위험이 있다.
 * STRING 을 권장하는 이유가 바로 이 때문이다.
 */
