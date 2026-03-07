package com.studyolle.modules.board.service;

import com.studyolle.modules.account.entity.Account;
import com.studyolle.modules.board.dto.BoardCommentForm;
import com.studyolle.modules.board.dto.BoardForm;
import com.studyolle.modules.board.entity.Board;
import com.studyolle.modules.board.entity.BoardCategory;
import com.studyolle.modules.board.entity.BoardComment;
import com.studyolle.modules.board.event.BoardCreatedEvent;
import com.studyolle.modules.board.repository.BoardCommentRepository;
import com.studyolle.modules.board.repository.BoardRepository;
import com.studyolle.modules.study.entity.Study;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 게시판(Board/BoardComment) 핵심 비즈니스 로직 서비스
 *
 * =============================================
 * 계층 구조
 * =============================================
 *
 * BoardController → BoardService → BoardRepository / BoardCommentRepository
 */
@Service
@Transactional
@RequiredArgsConstructor
public class BoardService {

    private final BoardRepository boardRepository;
    private final BoardCommentRepository boardCommentRepository;
    private final ApplicationEventPublisher eventPublisher;

    // ============================
    // 게시글 CRUD
    // ============================

    /**
     * 새 게시글을 생성합니다.
     * 공지사항(NOTICE)은 관리자만 작성 가능합니다.
     */
    public Board createBoard(Study study, Account account, BoardForm boardForm) {
        if (boardForm.getCategory() == BoardCategory.NOTICE && !study.isManagerOf(account)) {
            throw new AccessDeniedException("공지사항은 관리자만 작성할 수 있습니다.");
        }

        Board board = Board.builder()
                .study(study)
                .createdBy(account)
                .title(boardForm.getTitle())
                .content(boardForm.getContent())
                .category(boardForm.getCategory())
                .createdDateTime(LocalDateTime.now())
                .build();

        Board savedBoard = boardRepository.save(board);
        eventPublisher.publishEvent(new BoardCreatedEvent(savedBoard));

        return savedBoard;
    }

    /**
     * 게시글 목록을 페이징 조회합니다.
     * category가 null이면 전체, 아니면 해당 카테고리만 필터링합니다.
     */
    public Page<Board> getBoards(Study study, BoardCategory category, Pageable pageable) {
        if (category != null) {
            return boardRepository.findByStudyAndCategoryOrderByCreatedDateTimeDesc(study, category, pageable);
        }
        return boardRepository.findByStudyOrderByCreatedDateTimeDesc(study, pageable);
    }

    /**
     * 게시글 상세 조회 (댓글 포함, EntityGraph 적용)
     */
    public Board getBoardWithComments(Long id) {
        return boardRepository.findBoardWithCommentsById(id)
                .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다. id=" + id));
    }

    /**
     * 게시글 수정 (작성자 본인 또는 관리자만)
     */
    public void updateBoard(Board board, Account account, BoardForm boardForm) {
        if (!board.canModify(account)) {
            throw new AccessDeniedException("게시글을 수정할 권한이 없습니다.");
        }

        board.setTitle(boardForm.getTitle());
        board.setContent(boardForm.getContent());
        board.setCategory(boardForm.getCategory());
        board.setModifiedDateTime(LocalDateTime.now());
    }

    /**
     * 게시글 삭제 (cascade로 댓글도 함께 삭제)
     */
    public void deleteBoard(Board board, Account account) {
        if (!board.canModify(account)) {
            throw new AccessDeniedException("게시글을 삭제할 권한이 없습니다.");
        }

        boardRepository.delete(board);
    }

    // ============================
    // 댓글 CRUD
    // ============================

    public BoardComment addComment(Board board, Account account, BoardCommentForm commentForm) {
        BoardComment comment = BoardComment.builder()
                .board(board)
                .account(account)
                .content(commentForm.getContent())
                .createdDateTime(LocalDateTime.now())
                .build();

        board.increaseCommentCount();

        return boardCommentRepository.save(comment);
    }

    public void deleteComment(BoardComment comment, Account account) {
        if (!comment.canDelete(account)) {
            throw new AccessDeniedException("댓글을 삭제할 권한이 없습니다.");
        }

        Board board = comment.getBoard();
        board.decreaseCommentCount();

        boardCommentRepository.delete(comment);
    }
}