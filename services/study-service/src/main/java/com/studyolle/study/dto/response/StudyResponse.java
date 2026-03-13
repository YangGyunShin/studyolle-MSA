package com.studyolle.study.dto.response;

import com.studyolle.study.entity.JoinType;
import com.studyolle.study.entity.Study;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Set;

// 스터디 상세 조회 API 의 응답 DTO.
// Study 엔티티의 모든 정보를 포함하는 "풀 버전" 응답이다.
// 목록에서는 StudySummaryResponse(경량 버전)를 쓰고,
// 상세 페이지에서는 이 클래스를 사용한다.
@Getter
@Builder
public class StudyResponse {

    private Long id;
    private String path;
    private String title;
    private String shortDescription;
    private String fullDescription;    // 상세 페이지에서만 필요한 본문 (크기가 크다)
    private String image;              // 배너 이미지 (Base64 인코딩, 크기가 크다)
    private boolean useBanner;
    private boolean published;
    private boolean closed;
    private boolean recruiting;
    private JoinType joinType;
    private int memberCount;
    private LocalDateTime publishedDateTime;

    // 관리자/멤버/태그/지역은 실제 객체 대신 ID Set 으로만 반환한다.
    // 이유: study-service 는 ID 만 알고 있고, 이름/프로필 등 상세 정보는
    // 각각 account-service, metadata-service 가 소유하고 있다.
    // 프론트엔드가 이 ID 목록을 받아 필요한 정보를 각 서비스에 별도로 요청한다.
    private Set<Long> managerIds;
    private Set<Long> memberIds;
    private Set<Long> tagIds;
    private Set<Long> zoneIds;

    // Study 엔티티를 이 DTO 로 변환하는 정적 팩터리 메서드.
    // 서비스/컨트롤러에서 StudyResponse.from(study) 한 줄로 변환한다.
    // 이 메서드 안에서만 엔티티 필드에 접근하므로 엔티티 구조 변경 시 이 메서드만 수정하면 된다
    public static StudyResponse from(Study study) {
        return StudyResponse.builder()
                .id(study.getId())
                .path(study.getPath())
                .title(study.getTitle())
                .shortDescription(study.getShortDescription())
                .fullDescription(study.getFullDescription())
                .image(study.getImage())
                .useBanner(study.isUseBanner())
                .published(study.isPublished())
                .closed(study.isClosed())
                .recruiting(study.isRecruiting())
                .joinType(study.getJoinType())
                .memberCount(study.getMemberCount())
                .publishedDateTime(study.getPublishedDateTime())
                .managerIds(study.getManagerIds())
                .memberIds(study.getMemberIds())
                .tagIds(study.getTagIds())
                .zoneIds(study.getZoneIds())
                .build();
    }
}

/*
 * [엔티티를 직접 반환하지 않고 DTO 로 변환해서 반환하는 이유]
 *
 * 엔티티(Study)를 컨트롤러에서 그대로 @ResponseBody 로 반환하면 여러 문제가 생긴다.
 *
 * 첫째, 순환 참조 문제다.
 * Study 가 JoinRequest 를 참조하고, JoinRequest 가 다시 Study 를 참조하면
 * Jackson 이 JSON 으로 변환할 때 무한 루프에 빠진다.
 * DTO 는 단방향 참조이므로 이 문제가 없다.
 *
 * 둘째, 지연 로딩(Lazy Loading) 문제다.
 * @ManyToOne(fetch = LAZY) 로 선언된 연관 관계는 실제로 접근하기 전까지 로딩되지 않는다.
 * 트랜잭션이 끝난 컨트롤러에서 Jackson 이 엔티티를 직렬화하려고 접근하면
 * LazyInitializationException 이 발생한다.
 * DTO 는 트랜잭션 내에서 필요한 필드만 미리 복사하므로 이 문제가 없다.
 *
 * 셋째, API 응답 구조와 엔티티 구조가 달라질 수 있다.
 * 엔티티는 DB 설계에 최적화되어 있고, API 응답은 클라이언트 요구에 맞춰 설계된다.
 * DTO 를 두면 두 구조를 독립적으로 발전시킬 수 있다.
 *
 *
 * [정적 팩터리 메서드 from() 패턴]
 *
 * new StudyResponse(...) 대신 StudyResponse.from(study) 를 쓰면:
 * 1. 메서드 이름이 의도("Study 에서 변환")를 표현한다.
 * 2. 변환 로직이 DTO 클래스 안에 캡슐화된다. 외부에서 필드를 하나씩 set 하는 코드가 없다.
 * 3. Study 엔티티 구조가 바뀌어도 from() 메서드만 수정하면 된다.
 *    컨트롤러, 서비스는 from(study) 호출만 하고 있으므로 영향을 받지 않는다.
 */
