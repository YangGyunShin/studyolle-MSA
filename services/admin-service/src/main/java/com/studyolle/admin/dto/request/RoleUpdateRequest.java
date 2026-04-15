package com.studyolle.admin.dto.request;

/**
 * 관리자 회원 권한 변경 요청 DTO.
 *
 * 같은 이름의 record 가 account-service 에도 존재한다.
 * MSA 의 원칙상 두 서비스는 서로의 클래스를 import 하지 않으며,
 * Jackson 이 JSON 으로 직렬화/역직렬화할 때 필드 이름이 일치하기만 하면
 * 양쪽이 같은 모양의 객체를 주고받을 수 있다.
 * "계약은 JSON 형태이지 Java 클래스가 아니다" 라는 점이 모놀리스와 가장 다른 부분이다.
 */
public record RoleUpdateRequest(String role) {
}