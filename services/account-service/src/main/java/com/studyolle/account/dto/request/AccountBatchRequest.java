package com.studyolle.account.dto.request;

import java.util.List;

/**
 * POST /internal/accounts/batch 요청 본문.
 *
 * 왜 record 인가:
 *   - 요청 DTO 는 불변이어야 한다 (컨트롤러로 넘어온 뒤 값이 바뀌면 안 된다).
 *   - getter / equals / hashCode / toString 이 자동 생성된다.
 *   - 접근자는 getIds() 가 아니라 ids() 임에 주의.
 *
 * 왜 List<Long> 인가:
 *   - JSON 배열 [1, 5, 12] 를 그대로 받기 위해서.
 *   - Set 으로 받으면 중복이 자동 제거되지만,
 *     "입력은 최대한 원본 그대로 받고 중복 제거는 service 에서"
 *     라는 책임 분리 관점에서 List 가 낫다.
 */
public record AccountBatchRequest(List<Long> ids) {
}