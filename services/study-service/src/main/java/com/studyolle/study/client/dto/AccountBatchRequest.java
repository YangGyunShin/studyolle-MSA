package com.studyolle.study.client.dto;

import java.util.List;
import java.util.Set;

/**
 * POST /internal/accounts/batch 요청 본문.
 *
 * [왜 account-service 의 AccountBatchRequest 를 import 하지 않는가]
 * anti-corruption layer 원칙에 따라 서비스 간 Java 클래스를 공유하지 않는다.
 * 두 클래스가 지금은 구조가 같지만, 언젠가 각자의 이유로 변경될 수 있으므로 각 서비스가 자기 몫의 DTO 를 따로 정의한다.
 *
 * [왜 List<Long> 이 아니라 이번엔 static 메서드로 Set 을 받는가]
 * 호출부(컨트롤러) 에서 managerIds + memberIds 를 합쳐 Set<Long> 으로 만든 뒤 넘긴다.
 * 그런데 Jackson 이 JSON 으로 직렬화할 때는 Set/List 구분 없이 배열이 된다.
 * 그래서 record 의 필드 타입은 List<Long> 으로 두되, 편의를 위해 of(Set<Long>) 팩토리만 하나 추가했다.
 * 호출 측이 Set 을 그대로 넘기면 내부에서 List 로 바꿔 저장한다.
 */
public record AccountBatchRequest(List<Long> ids) {

    /**
     * 호출 측이 Set<Long> 을 갖고 있을 때 편하게 감쌀 수 있도록 제공하는 팩토리 메서드.
     * JSON 직렬화 결과는 List<Long> 과 동일한 배열이다.
     */
    public static AccountBatchRequest of(Set<Long> ids) {
        return new AccountBatchRequest(List.copyOf(ids));
    }
}