package com.studyolle.adminfrontend.member.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * admin-service 의 AccountAdminDto 를 admin-frontend 가 역직렬화하기 위한 미러 DTO.
 *
 * AccountSummaryResponse(account-service) → AccountAdminDto(admin-service) → AdminMemberDto(이 파일)
 * 로 동일한 구조가 세 번 복사되는 것이 맞다.
 * 각 서비스가 자기 경계 안에서 필요한 형태로 DTO 를 소유한다는 MSA 원칙 때문이다.
 * 실무에서는 OpenAPI 스펙에서 클라이언트 코드를 자동 생성해 복사 작업을 자동화하기도 한다.
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