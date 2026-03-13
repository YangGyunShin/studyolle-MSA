package com.studyolle.study.dto.response;

import com.studyolle.study.entity.JoinRequest;
import com.studyolle.study.entity.JoinRequestStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

// 스터디 가입 신청 내역 응답 DTO.
// 관리자가 가입 신청 목록을 조회할 때 반환된다.
// JoinRequest 엔티티를 직접 반환하지 않고 DTO 로 변환하는 이유는
// 엔티티에는 Study 연관 관계가 있어 직렬화 시 불필요한 데이터가 딸려 나올 수 있기 때문이다.
@Getter
@Builder
public class JoinRequestResponse {

    private Long id;
    private Long accountId;           // 신청자 계정 ID
    private String accountNickname;   // 신청 시점에 저장된 닉네임 (비정규화 필드)
    private JoinRequestStatus status; // 현재 상태: PENDING, APPROVED, REJECTED
    private LocalDateTime requestedAt;  // 신청 일시
    private LocalDateTime processedAt;  // 승인/거절 처리 일시. PENDING 이면 null

    // JoinRequest 엔티티에서 이 DTO 로 변환하는 정적 팩터리 메서드
    public static JoinRequestResponse from(JoinRequest request) {
        return JoinRequestResponse.builder()
                .id(request.getId())
                .accountId(request.getAccountId())
                .accountNickname(request.getAccountNickname())
                .status(request.getStatus())
                .requestedAt(request.getRequestedAt())
                .processedAt(request.getProcessedAt())
                .build();
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
 *
 *
 * [processedAt 이 null 일 수 있는 이유]
 *
 * 신청이 PENDING 상태일 때는 아직 승인/거절 처리가 되지 않았으므로 processedAt 이 null 이다.
 * @JsonInclude 를 선언하지 않았으므로 JSON 에 "processedAt": null 이 포함된다.
 * 프론트엔드는 null 체크를 통해 아직 처리되지 않은 신청임을 알 수 있다.
 */
