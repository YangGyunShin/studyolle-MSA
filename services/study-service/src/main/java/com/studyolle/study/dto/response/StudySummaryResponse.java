package com.studyolle.study.dto.response;

import com.studyolle.study.entity.Study;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Set;

// 스터디 목록(카드형 UI)에서 사용하는 경량 응답 DTO.
// StudyResponse 의 모든 필드가 필요하지 않은 목록 조회에서 사용한다.
// fullDescription 과 image 는 크기가 커서 목록 응답에 포함하면 응답 크기가 불필요하게 커진다.
// 필요한 필드만 골라서 응답하는 것이 API 설계의 기본 원칙이다.
@Getter
@Builder
public class StudySummaryResponse {

    private Long id;
    private String path;
    private String title;
    private String shortDescription;   // 카드에 표시되는 한 줄 소개
    private boolean published;
    private boolean closed;
    private boolean recruiting;
    private int memberCount;           // 카드에 표시되는 현재 멤버 수
    private LocalDateTime publishedDateTime;
    private Set<Long> tagIds;          // 카드에 태그 배지를 표시할 때 사용
    private Set<Long> zoneIds;         // 카드에 지역 배지를 표시할 때 사용

    // Study 엔티티에서 목록용 필드만 추려 변환하는 정적 팩터리 메서드
    public static StudySummaryResponse from(Study study) {
        return StudySummaryResponse.builder()
                .id(study.getId())
                .path(study.getPath())
                .title(study.getTitle())
                .shortDescription(study.getShortDescription())
                .published(study.isPublished())
                .closed(study.isClosed())
                .recruiting(study.isRecruiting())
                .memberCount(study.getMemberCount())
                .publishedDateTime(study.getPublishedDateTime())
                .tagIds(study.getTagIds())
                .zoneIds(study.getZoneIds())
                .build();
    }
}

/*
 * [StudyResponse 와 StudySummaryResponse 를 분리하는 이유]
 *
 * 스터디 카드 목록을 요청할 때 fullDescription(에디터 HTML)과 image(Base64 문자열)가
 * 함께 내려오면 응답 크기가 수십 배 커진다.
 * 예를 들어 카드 9개를 보여주는 추천 목록에서 각 스터디의 상세 HTML 이 포함되면
 * 클라이언트는 쓰지도 않는 데이터를 수신하고 파싱하는 데 시간을 낭비한다.
 *
 * 응답 DTO 를 용도에 맞게 분리하면:
 * - 목록 API: StudySummaryResponse (경량)
 * - 상세 API: StudyResponse (전체)
 *
 * 이렇게 하면 네트워크 비용이 줄고 프론트엔드 렌더링 속도가 빨라진다.
 * 이 패턴은 API 설계에서 "프로젝션(Projection)" 이라고 부른다.
 *
 *
 * [tagIds, zoneIds 를 목록에도 포함하는 이유]
 *
 * 카드 UI 에는 태그와 지역 배지가 표시된다.
 * 이를 위해 tagIds 와 zoneIds 가 필요하다.
 * 다만 실제 이름(예: "스프링", "서울")은 metadata-service 가 가지고 있으므로,
 * 프론트엔드는 이 ID 들을 가지고 metadata-service 에 이름을 따로 요청한다.
 * (또는 화면을 처음 로드할 때 전체 태그/지역 목록을 캐싱해두고 ID 로 매핑한다)
 */
