package com.studyolle.study.client.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * account-service 가 반환하는 AccountSummaryResponse JSON 을 study-service 쪽에서 역직렬화해 받을 때 쓰는 DTO.
 *
 * [왜 account-service 의 AccountSummaryResponse 를 직접 import 하지 않는가]
 * MSA 원칙상 두 서비스는 서로의 Java 클래스를 공유하지 않는다.
 * 계약은 JSON 형태이지 Java 클래스가 아니다.
 * 공유 라이브러리를 만들면 "분산 모놀리스" 가 되어 두 서비스의 배포 독립성이 사라진다.
 * 그래서 각 서비스가 자기 몫의 DTO 를 따로 정의한다 (anti-corruption layer).
 *
 * [왜 필드가 네 개뿐인가]
 * account-service 는 실제로 id, nickname, email, bio, profileImage, role, emailVerified, tagCount, zoneCount 등 더 많은 필드를 돌려준다.
 * 그러나 study-service 가 멤버 카드 렌더링에 필요한 건 id/nickname/profileImage/bio 넷뿐이다.
 * Jackson 기본 설정(FAIL_ON_UNKNOWN_PROPERTIES=false) 이 나머지 필드를 조용히 버리므로
 * 여기에 쓰지 않은 필드가 응답에 있어도 문제가 되지 않는다.
 * 이 DTO 는 "내가 관심 있는 것만" 을 선언하는 역할을 한다.
 *
 * [왜 record 가 아니고 @Getter + @NoArgsConstructor + @Setter 인가]
 * Jackson 이 record 를 역직렬화하는 것도 물론 가능하지만, Feign + Spring Boot 환경에서는 일반 클래스 + 기본 생성자 방식이 가장 안전하다.
 * (record 는 Jackson 버전에 따라 가끔 예상치 못한 동작을 일으킨다.)
 * 또한 기존 EventSummaryDto 등도 동일한 스타일이므로 프로젝트 내 일관성을 지킨다.
 */
@Getter
@Setter
@NoArgsConstructor
public class AccountSummaryDto {

    private Long id;
    private String nickname;
    private String profileImage;
    private String bio;
}