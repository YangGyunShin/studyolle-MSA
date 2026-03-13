package com.studyolle.study.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

// 스터디 소개 수정 요청 DTO.
// path, title 은 별도의 API 로 분리되어 있으므로 이 DTO 에는 포함하지 않는다.
// @NoArgsConstructor: Jackson 역직렬화 시 기본 생성자가 필요하다.
//   @Getter 만 있으면 Jackson 은 reflection 으로 필드에 값을 주입하거나
//   기본 생성자 + setter 를 사용하는데, setter 가 없으므로 기본 생성자를 명시한다.
@Getter
@NoArgsConstructor
public class UpdateStudyDescriptionRequest {

    // 카드 목록에 표시되는 한 줄 소개. 최대 100자 제한
    @NotBlank
    @Length(max = 100)
    private String shortDescription;

    // 스터디 상세 페이지 본문. 에디터 HTML 포함 가능. 길이 제한 없음
    @NotBlank
    private String fullDescription;
}

/*
 * [왜 소개 수정과 경로/제목 수정을 별도 API 로 분리했는가?]
 *
 * 설정 페이지에서 사용자가 수정할 수 있는 항목들은 성격이 다르다.
 *
 * shortDescription, fullDescription: 언제든지 자유롭게 수정 가능한 콘텐츠.
 * path: URL 이 바뀌므로 북마크, 외부 링크가 모두 깨진다. 신중하게 다뤄야 한다.
 * title: 검색 인덱스, 알림 메시지에 영향을 줄 수 있다.
 *
 * 항목별로 API 를 분리하면 각 수정 작업의 영향 범위를 명확히 하고,
 * 프론트엔드에서 필요한 항목만 선택적으로 수정 요청을 보낼 수 있다.
 * 하나의 거대한 "스터디 전체 수정" API 보다 변경 의도가 명확해진다.
 *
 *
 * [@NoArgsConstructor 와 Jackson 역직렬화의 관계]
 *
 * Jackson 이 JSON → 객체 변환(역직렬화)을 할 때 기본적으로 두 가지 방식을 쓴다:
 * 1. 기본 생성자로 빈 객체를 만든 뒤 setter 또는 reflection 으로 필드에 값을 주입
 * 2. @JsonCreator + @JsonProperty 가 붙은 생성자를 직접 호출
 *
 * 이 클래스는 setter 가 없고 @JsonCreator 도 없으므로,
 * @NoArgsConstructor 로 기본 생성자를 만들어 방식 1을 허용한다.
 * Lombok 이 없으면 Jackson 은 역직렬화에 실패하고 HttpMessageNotReadableException 이 발생한다.
 */
