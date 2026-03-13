package com.studyolle.study.dto.response;

import com.studyolle.study.entity.Study;
import lombok.Builder;
import lombok.Getter;

import java.util.Set;

// /internal/** 경로 전용 응답 DTO.
// 외부 클라이언트가 아닌 다른 서비스(event-service, admin-service)가 호출할 때 반환된다.
// StudyResponse 의 모든 필드가 아닌 서비스 간 통신에 꼭 필요한 필드만 포함한다.
@Getter
@Builder
public class StudyInternalResponse {

    private Long id;
    private String path;
    private String title;
    private boolean published;
    private boolean closed;
    private boolean recruiting;

    // event-service 가 "이 사람이 스터디 관리자인가?" 를 확인할 때 사용한다.
    // 모임(Event) 생성은 스터디 관리자만 할 수 있으므로 이 목록이 필요하다.
    private Set<Long> managerIds;

    // Study 엔티티에서 내부 통신에 필요한 필드만 추려 변환하는 정적 팩터리 메서드
    public static StudyInternalResponse from(Study study) {
        return StudyInternalResponse.builder()
                .id(study.getId())
                .path(study.getPath())
                .title(study.getTitle())
                .published(study.isPublished())
                .closed(study.isClosed())
                .recruiting(study.isRecruiting())
                .managerIds(study.getManagerIds())
                .build();
    }
}

/*
 * [내부 통신 전용 DTO 를 별도로 분리하는 이유]
 *
 * StudyResponse 를 그대로 내부 통신에 써도 동작은 한다.
 * 하지만 그렇게 하면 두 가지 문제가 생긴다.
 *
 * 첫째, 불필요한 데이터 전송이다.
 * fullDescription(에디터 HTML), image(Base64 이미지), memberIds 등은
 * event-service 가 전혀 필요로 하지 않는 데이터다.
 * 서비스 간 내부 통신에서 이런 데이터를 주고받으면 네트워크 비용이 늘고
 * 직렬화/역직렬화 시간이 길어진다.
 *
 * 둘째, 응답 구조 결합도가 높아진다.
 * StudyResponse 의 필드가 바뀌면 이를 사용하는 모든 서비스가 영향을 받는다.
 * 내부 통신 전용 DTO 를 쓰면 외부 API 의 응답 구조와 내부 통신 구조를
 * 독립적으로 변경할 수 있다.
 *
 *
 * [왜 fullDescription, image, memberIds 는 제외했는가?]
 *
 * fullDescription, image: 화면 렌더링용 데이터다. 다른 서비스가 이 값을 처리할 일이 없다.
 *
 * memberIds: event-service 는 모임(Event) 참가 여부를 Enrollment 라는 자체 엔티티로 관리한다.
 * "이 사람이 스터디 멤버인가?" 라는 질문은 event-service 입장에서는 의미가 없다.
 * event-service 가 필요한 것은 "이 사람이 모임을 만들 수 있는 관리자인가?" 뿐이다.
 *
 * managerIds: event-service 가 모임 생성 전 관리자 권한을 검증하기 위해 반드시 필요하다.
 * closed: 종료된 스터디에서는 새 모임을 만들 수 없으므로 필요하다.
 */
