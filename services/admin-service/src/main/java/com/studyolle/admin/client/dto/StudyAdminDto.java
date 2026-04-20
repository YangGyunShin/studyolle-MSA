package com.studyolle.admin.client.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * study-service 의 StudyAdminResponse 를 역직렬화하기 위한 미러 DTO.
 *
 * [왜 복사하는가 — 독립 배포 원칙]
 * admin-service 가 study-service 의 DTO 클래스를 import 할 수 있게 하려면 공유 라이브러리가 필요하다.
 * 그러면 두 서비스가 그 라이브러리 버전에 묶여 "분산 모놀리스" 가 되어버려 MSA 의 독립 배포 이점이 사라진다.
 * 필드 몇 개 복사가 결합보다 낫다 — 이것이 MSA 의 표준 관점이다.
 *
 * [필드 이름 일치가 핵심]
 * Jackson 은 JSON 키와 Java 필드 이름을 매칭해 값을 채운다.
 * 이름을 틀리면 조용히 null 이 들어가므로 (TroubleShooting_018 과 같은 종류의 버그) 복사할 때 이름을 그대로 가져오는 것이 중요하다.
 */
@Getter
@Setter
@NoArgsConstructor
public class StudyAdminDto {

    private Long id;
    private String path;
    private String title;
    private String shortDescription;

    private boolean published;
    private boolean closed;
    private boolean recruiting;

    private int memberCount;

    // LocalDateTime — Jackson 기본 설정으로 ISO-8601 문자열 ↔ LocalDateTime 변환이 자동으로 이루어진다.
    // spring-boot-starter-web 에 포함된 jackson-datatype-jsr310 덕분.
    private LocalDateTime publishedDateTime;
    private LocalDateTime closedDateTime;
}