package com.studyolle.modules.board.repository;

import com.studyolle.modules.board.entity.BoardComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 댓글(BoardComment) 데이터 접근 계층
 *
 * 댓글 목록 조회는 Board.comments + EntityGraph로 처리하므로,
 * 이 Repository는 주로 댓글 삭제와 단건 조회에 사용됩니다.
 */
@Transactional(readOnly = true)
public interface BoardCommentRepository extends JpaRepository<BoardComment, Long> {
}