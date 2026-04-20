package com.studyolle.study.dto.response;

import com.studyolle.study.entity.Study;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 관리자 전용 스터디 요약 응답 DTO.
 *
 * [기존 DTO 를 재사용하지 않고 별도로 만든 이유]
 * - StudySummaryResponse: 공개 목록용.
 *   closed/published 같은 관리 상태 정보가 부족하거나 외부 노출을 전제로 설계되어 있다.
 * - StudyInternalResponse: 상세 페이지용.
 *   managerIds/memberIds 전체 컬렉션을 포함해 목록 조회 용도로는 너무 무겁고, @ElementCollection 접근으로 쿼리가 늘어난다.
 *
 * 관리자 목록은 "상태를 한눈에 보고 판단한다" 는 고유 목적이 있으므로, 딱 그 용도에 최적화된 필드만 담는 전용 DTO 가 가장 깔끔하다.
 * 앞으로 관리자용 필드 (예: 신고 건수) 가 추가되어도 공개 DTO 를 건드리지 않고 이 DTO 만 확장하면 된다.
 *
 * [왜 memberCount 는 포함하지만 memberIds 는 포함하지 않는가]
 * 목록 화면에서 "멤버가 몇 명인지" 는 중요하지만, "누가 멤버인지" 는 행마다 수십 개의 id 를 내려주는 부담만 늘린다.
 * memberCount 는 엔티티에 비정규화 필드로 저장되어 있어 컬렉션 테이블에 접근할 필요도 없다 — Study 엔티티 주석의 "비정규화" 설명 참고.
 *
 * [현재 시각(now)을 프론트에서 정하지 않고 포함하지 않는 이유]
 * "종료됨" 배지는 closed 값만으로 결정되므로 현재 시각 비교가 불필요하다.
 * "공개됨" 도 published 값만 보면 된다.
 * 상태 판정을 서버 시각 기준으로 하고 싶은 경우가 생기면 그때 필드를 추가하면 된다.
 * 지금은 필요가 없다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudyAdminResponse {

    private Long id;
    private String path;
    private String title;
    private String shortDescription;

    // 상태 플래그 — UI 배지 렌더링에 사용
    private boolean published;
    private boolean closed;
    private boolean recruiting;

    // 비정규화된 멤버 수 — 컬렉션 테이블 접근 없이 바로 조회 가능
    private int memberCount;

    // 시각 정보 — 정렬 기준 및 UI 표시용
    private LocalDateTime publishedDateTime;
    private LocalDateTime closedDateTime;

    /**
     * 엔티티 → 응답 DTO 정적 팩토리.
     *
     * [왜 정적 팩토리 패턴인가]
     * 엔티티를 "어떻게 응답 형태로 바꿀지" 의 규칙을 한 곳에 모아두기 위함이다.
     * 여러 컨트롤러에서 각자 엔티티를 손으로 빌드해 반환하면 어느 순간 같은 엔티티가 조금씩 다른 모양의 DTO 로 튀어나오는 불일치가 생긴다.
     * from() 한 곳에서 변환하면 그런 버그가 원천 봉쇄된다.
     */
    public static StudyAdminResponse from(Study study) {
        return StudyAdminResponse.builder()
                .id(study.getId())
                .path(study.getPath())
                .title(study.getTitle())
                .shortDescription(study.getShortDescription())
                .published(study.isPublished())
                .closed(study.isClosed())
                .recruiting(study.isRecruiting())
                .memberCount(study.getMemberCount())
                .publishedDateTime(study.getPublishedDateTime())
                .closedDateTime(study.getClosedDateTime())
                .build();
    }
}