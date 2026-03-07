package com.studyolle.modules.board.dto;

import com.studyolle.modules.board.entity.BoardCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

/**
 * 게시글 작성/수정 폼 DTO
 *
 * [데이터 흐름]
 *   [생성] 웹 폼 → BoardForm → @Valid → BoardService.createBoard() → Board → DB
 *   [수정] DB → Board → ModelMapper → BoardForm → 웹 폼 → 수정 → @Valid → dirty checking
 */
@Data
public class BoardForm {

    @NotBlank(message = "제목을 입력하세요.")
    @Length(min = 2, max = 100, message = "제목은 2자 이상 100자 이내로 입력하세요.")
    private String title;

    @NotBlank(message = "내용을 입력하세요.")
    private String content;

    @NotNull(message = "카테고리를 선택하세요.")
    private BoardCategory category;
}