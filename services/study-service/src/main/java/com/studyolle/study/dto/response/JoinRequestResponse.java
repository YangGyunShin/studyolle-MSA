package com.studyolle.study.dto.response;

import com.studyolle.study.entity.JoinRequest;
import com.studyolle.study.entity.JoinRequestStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 가입 신청 응답 DTO
 *
 * [모노리틱 참조]
 * StudySettingsController.joinRequestsForm() 에서 모델에 담아 뷰에 전달하던 것을
 * API 응답 DTO 로 전환한 형태.
 */
@Getter
@Builder
public class JoinRequestResponse {

    private Long id;
    private Long accountId;
    private String accountNickname;
    private JoinRequestStatus status;
    private LocalDateTime requestedAt;
    private LocalDateTime processedAt;

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