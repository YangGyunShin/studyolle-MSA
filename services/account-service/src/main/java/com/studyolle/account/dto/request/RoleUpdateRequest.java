package com.studyolle.account.dto.request;

/**
 * 회원 권한 변경 요청 DTO.
 *
 * record 를 쓴 이유: 요청 본문은 불변이어야 하고,
 * equals/hashCode/toString 이 자동 생성되며,
 * getter 도 role() 형태로 자동 제공된다.
 * setter 가 없어 컨트롤러에서 받은 후 누가 값을 바꿔치기할 위험도 없다.
 *
 * 검증 어노테이션(@NotBlank 등)을 굳이 붙이지 않은 이유는
 * AccountInternalService.updateRole() 안에서 더 풍부한 도메인 검증 (ROLE_ADMIN/ROLE_USER 두 값만 허용)을 수행하기 때문이다.
 */
public record RoleUpdateRequest(String role) {
}