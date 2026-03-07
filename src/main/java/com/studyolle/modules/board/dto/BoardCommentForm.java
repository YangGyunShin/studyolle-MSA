package com.studyolle.modules.board.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

/**
 * 댓글 작성 폼 DTO
 */
@Data
public class BoardCommentForm {

    @NotBlank(message = "댓글 내용을 입력하세요.")
    @Length(max = 500, message = "댓글은 500자 이내로 입력하세요.")
    private String content;
}