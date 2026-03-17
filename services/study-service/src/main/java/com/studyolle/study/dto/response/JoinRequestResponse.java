package com.studyolle.study.dto.response;

import com.studyolle.study.entity.JoinRequest;
import lombok.Builder;
import lombok.Getter;

import java.time.format.DateTimeFormatter;

// /internal/studies/{path}/join-requests 응답 DTO.
// frontend-service 의 JoinRequestDto 와 구조 동일.
//
// JoinRequest.accountNickname 이 비정규화 저장되어 있으므로
// account-service 호출 없이 nickname 을 바로 채울 수 있다.
@Getter
@Builder
public class JoinRequestResponse {

    private Long id;
    private MemberInfo account;     // 신청자 정보 (nickname 포함)
    private String requestedAt;     // 포맷된 날짜 문자열 ("3월 16일 14:30")

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("M월 d일 HH:mm");

    public static JoinRequestResponse from(JoinRequest joinRequest) {
        return JoinRequestResponse.builder()
                .id(joinRequest.getId())
                .account(MemberInfo.builder()
                        .id(joinRequest.getAccountId())
                        .nickname(joinRequest.getAccountNickname())  // 비정규화 값 사용
                        .build())
                .requestedAt(joinRequest.getRequestedAt().format(FORMATTER))
                .build();
    }

    @Getter
    @Builder
    public static class MemberInfo {
        private Long id;
        private String nickname;
    }
}

/*
 * [accountNickname 이 이 DTO 에 포함된 배경]
 *
 * 관리자가 가입 신청 목록을 볼 때 신청자 이름이 화면에 표시되어야 한다.
 * 신청자 이름을 얻으려면 accountId 로 account-service 를 호출해야 하는데,
 * 신청 목록이 10건이라면 10번의 Feign 요청이 발생한다.
 *
 * JoinRequest 엔티티의 accountNickname 은 신청 시점에 저장해둔 닉네임 복사본이다.
 * 이 값이 있으면 account-service 를 추가로 호출하지 않고도 목록을 완성할 수 있다.
 * 이 패턴을 "비정규화(Denormalization)" 라고 부른다.
 *
 * 비정규화의 단점은 원본(account-service 의 nickname)이 바뀌어도 복사본은 바뀌지 않는다는 것이다.
 * 그러나 "가입 신청 시점의 닉네임" 은 변경될 필요가 없는 이력 데이터이므로 허용되는 트레이드오프다.
 */
