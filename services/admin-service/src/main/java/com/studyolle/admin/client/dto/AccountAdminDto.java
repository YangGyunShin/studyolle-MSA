package com.studyolle.admin.client.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * account-service 의 AccountSummaryResponse 를 역직렬화하기 위한 미러 DTO.
 *
 * [왜 복사하는가 — MSA 의 독립 배포 원칙]
 * admin-service 가 account-service 의 DTO 클래스를 import 할 수 있게 만들려면
 * 공유 라이브러리를 두어야 한다. 그렇게 하면 두 서비스가 그 라이브러리 버전에 묶여
 * 사실상 "분산 모놀리스" 가 되어버린다. MSA 의 핵심 이점인 독립 배포가 사라진다.
 * 차라리 필드 몇 개를 복사하는 것이 결합보다 낫다는 것이 MSA 의 표준 관점이다.
 *
 * [필드 이름이 반드시 일치해야 하는 이유]
 * Jackson 은 JSON 의 키 이름과 Java 필드 이름을 매칭해서 값을 채운다.
 * account-service 가 { "nickname": "양균" } 을 보내면 이 클래스에도 nickname 필드가 있어야 한다.
 * 이름을 틀리면 Jackson 은 에러를 내지 않고 조용히 null 을 넣어두는데, 이게 TroubleShooting_018 에서
 * 겪은 "필드가 반영되지 않는 조용한 버그" 와 같은 종류다. 복사할 때 특히 조심해야 한다.
 *
 * [Setter 가 필요한 이유]
 * Jackson 의 기본 역직렬화 방식은 "기본 생성자로 인스턴스를 만든 뒤 Setter 로 값을 채우기" 다.
 * @NoArgsConstructor 와 @Setter 가 모두 있어야 Jackson 이 이 DTO 를 채울 수 있다.
 * (또는 @JsonCreator + 생성자 방식을 쓸 수도 있지만, Setter 쪽이 더 단순하다)
 */
@Getter
@Setter
@NoArgsConstructor
public class AccountAdminDto {

    private Long id;
    private String nickname;
    private String email;
    private String bio;
    private String profileImage;
    private boolean emailVerified;

    // account-service 의 AccountSummaryResponse 에 "role" 필드가 추가되어 있어야 한다.
    // ROLE_USER 또는 ROLE_ADMIN 값을 가진다.
    private String role;

    private int tagCount;
    private int zoneCount;
}