package com.studyolle.frontend.admin.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * admin-service 의 AccountAdminDto 를 frontend-service 가 역직렬화하기 위한 미러 DTO.
 *
 * [같은 구조를 세 번 복사하는 것 같은데 괜찮나]
 * account-service 의 AccountSummaryResponse → admin-service 의 AccountAdminDto →
 * frontend-service 의 AdminMemberDto 로 세 번 복사되는 것이 맞다.
 * 각 서비스가 자기 경계 안에서 필요한 형태로 DTO 를 소유한다는 MSA 원칙 때문이다.
 *
 * 실무에서는 OpenAPI 스펙을 공유하고 그 스펙에서 각 언어별 클라이언트 코드를 자동 생성하는 방식
 * (openapi-generator 등) 으로 이 복사 작업을 자동화하기도 한다. 학습 단계에서는 수동 복사로
 * 구조를 직접 체감하는 것이 이해에 도움이 된다.
 */
@Getter
@Setter
@NoArgsConstructor
public class AdminMemberDto {
    private Long id;
    private String nickname;
    private String email;
    private String bio;
    private String profileImage;
    private boolean emailVerified;
    private String role;
    private int tagCount;
    private int zoneCount;
}