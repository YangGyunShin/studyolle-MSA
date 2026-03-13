package com.studyolle.study.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 태그 추가/제거 요청 DTO.
// tagTitle 문자열 하나만 받는다.
// 태그의 실제 ID 는 MetadataFeignClient 를 통해 metadata-service 에서 조회한다.
@Getter
@NoArgsConstructor
public class TagRequest {

    // 추가하거나 제거할 태그 이름. 예: "스프링", "자바"
    // metadata-service 에 이 이름을 보내면 ID 를 반환해준다 (없으면 새로 생성)
    @NotBlank
    private String tagTitle;
}

/*
 * [tagTitle 을 받고 ID 를 서버에서 조회하는 이유]
 *
 * 클라이언트(프론트엔드)는 화면에서 태그 이름을 입력하거나 선택한다.
 * 서버 DB 의 내부 ID 를 클라이언트가 직접 알고 보내도록 설계하면 두 가지 문제가 생긴다.
 *
 * 첫째, 클라이언트가 존재하지 않는 ID 를 임의로 보낼 수 있어 데이터가 깨진다.
 * 둘째, 새 태그를 만들 때(findOrCreate) ID 가 아직 없으므로 전송 자체가 불가능하다.
 *
 * 이름을 받고 서버에서 ID 로 변환하면 두 문제 모두 해결된다.
 * metadata-service 는 이름으로 태그를 찾고, 없으면 새로 만들어서 ID 를 반환한다.
 */
