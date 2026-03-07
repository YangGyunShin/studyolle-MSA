package com.studyolle.modules.tag;

import lombok.Data;

/**
 * ✅ 태그 추가/삭제 요청 시 JSON 데이터를 바인딩하는 DTO
 *
 * 프론트엔드(Tagify)에서 AJAX 요청으로 전송하는 JSON 본문을 수신한다.
 *
 * 요청 예시 (태그 추가):
 *   POST /settings/tags/add
 *   Content-Type: application/json
 *   Body: {"tagTitle": "Spring"}
 *
 * 사용처:
 *   - TagZoneController (Account의 관심 태그 추가/삭제)
 *     → @RequestBody TagForm tagForm
 *   - StudySettingsController (Study의 주제 태그 추가/삭제)
 *     → @RequestBody TagForm tagForm
 *
 * 바인딩 방식:
 *   - @RequestBody + Jackson ObjectMapper가 JSON → TagForm으로 자동 역직렬화
 *   - JSON 키("tagTitle")와 필드명(tagTitle)이 일치하면 자동 매핑
 *   - @Data(Lombok)가 getter/setter를 생성하므로 Jackson이 setter를 통해 값을 주입
 *
 * 설계 참고:
 *   - ZoneForm과 달리 단순한 문자열 하나만 전달받으므로 별도 파싱 로직이 없음
 *   - Bean Validation(@NotBlank 등)은 현재 적용되지 않음
 *     → AJAX 요청이므로 서버에서 DB 조회 결과(null 체크)로 유효성을 검증
 */
@Data
public class TagForm {

    /**
     * 프론트엔드에서 전송한 태그 제목
     *
     * 예: {"tagTitle": "Spring"} → tagTitle = "Spring"
     *
     * 이 값은 컨트롤러에서 다음과 같이 활용됨:
     *   - TagRepository.findByTitle(tagTitle) → 기존 태그 조회
     *   - Tag.builder().title(tagTitle).build() → 새 태그 생성
     */
    private String tagTitle;
}