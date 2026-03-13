package com.studyolle.study.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 태그 추가/제거 요청 DTO
 *
 * [모노리틱 참조: TagForm.java]
 * tagTitle 을 받아 MetadataFeignClient.findOrCreateTag() 로 전달한다.
 */
@Getter
@NoArgsConstructor
public class TagRequest {

    @NotBlank
    private String tagTitle;
}