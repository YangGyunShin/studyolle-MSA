package com.studyolle.modules.board.controller;

import com.studyolle.modules.account.entity.Account;
import com.studyolle.modules.board.dto.BoardCommentForm;
import com.studyolle.modules.board.dto.BoardForm;
import com.studyolle.modules.board.entity.Board;
import com.studyolle.modules.board.entity.BoardCategory;
import com.studyolle.modules.board.entity.BoardComment;
import com.studyolle.modules.board.repository.BoardCommentRepository;
import com.studyolle.modules.board.service.BoardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 스터디 내 게시판 기능을 담당하는 컨트롤러
 *
 * =============================================
 * URL 설계
 * =============================================
 *
 *   GET    /study/{path}/board                    게시글 목록
 *   GET    /study/{path}/board/new                새 게시글 작성 폼
 *   POST   /study/{path}/board/new                새 게시글 저장
 *   GET    /study/{path}/board/{boardId}           게시글 상세 (+ 댓글)
 *   GET    /study/{path}/board/{boardId}/edit       게시글 수정 폼
 *   POST   /study/{path}/board/{boardId}/edit       게시글 수정 저장
 *   DELETE /study/{path}/board/{boardId}           게시글 삭제
 *   POST   /study/{path}/board/{boardId}/comments  댓글 작성
 *   DELETE /study/{path}/board/{boardId}/comments/{commentId}  댓글 삭제
 */
@Controller
@RequestMapping("/study/{path}/board")
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;
    private final StudyService studyService;
    private final BoardCommentRepository boardCommentRepository;
    private final ModelMapper modelMapper;

    // ============================
    // 게시글 목록
    // ============================

    @GetMapping
    public String listBoards(Account account,
                             @PathVariable String path,
                             @RequestParam(required = false) BoardCategory category,
                             @PageableDefault(size = 10) Pageable pageable,
                             Model model) {

        Study study = studyService.getStudy(path);
        Page<Board> boards = boardService.getBoards(study, category, pageable);

        model.addAttribute(account);
        model.addAttribute(study);
        model.addAttribute("boards", boards);
        model.addAttribute("category", category);
        model.addAttribute("categories", BoardCategory.values());

        return "board/list";
    }

    // ============================
    // 게시글 작성
    // ============================

    @GetMapping("/new")
    public String newBoardForm(Account account,
                               @PathVariable String path,
                               Model model) {

        Study study = studyService.getStudy(path);
        model.addAttribute(account);
        model.addAttribute(study);
        model.addAttribute(new BoardForm());
        model.addAttribute("categories", BoardCategory.values());
        model.addAttribute("isManager", study.isManagerOf(account));

        return "board/form";
    }

    @PostMapping("/new")
    public String createBoard(Account account,
                              @PathVariable String path,
                              @Valid BoardForm boardForm,
                              Errors errors,
                              Model model,
                              RedirectAttributes redirectAttributes) {

        Study study = studyService.getStudy(path);

        if (errors.hasErrors()) {
            model.addAttribute(account);
            model.addAttribute(study);
            model.addAttribute("categories", BoardCategory.values());
            model.addAttribute("isManager", study.isManagerOf(account));
            return "board/form";
        }

        Board board = boardService.createBoard(study, account, boardForm);
        redirectAttributes.addFlashAttribute("message", "게시글을 작성했습니다.");

        return "redirect:/study/" + study.getEncodedPath() + "/board/" + board.getId();
    }

    // ============================
    // 게시글 상세
    // ============================

    @GetMapping("/{boardId}")
    public String viewBoard(Account account,
                            @PathVariable String path,
                            @PathVariable Long boardId,
                            Model model) {

        Study study = studyService.getStudy(path);
        Board board = boardService.getBoardWithComments(boardId);

        model.addAttribute(account);
        model.addAttribute(study);
        model.addAttribute("board", board);
        model.addAttribute(new BoardCommentForm());

        return "board/view";
    }

    // ============================
    // 게시글 수정
    // ============================

    @GetMapping("/{boardId}/edit")
    public String editBoardForm(Account account,
                                @PathVariable String path,
                                @PathVariable Long boardId,
                                Model model) {

        Study study = studyService.getStudy(path);
        Board board = boardService.getBoardWithComments(boardId);

        model.addAttribute(account);
        model.addAttribute(study);
        model.addAttribute("board", board);
        model.addAttribute("boardForm", modelMapper.map(board, BoardForm.class));
        model.addAttribute("categories", BoardCategory.values());
        model.addAttribute("isManager", study.isManagerOf(account));

        return "board/edit-form";
    }

    @PostMapping("/{boardId}/edit")
    public String updateBoard(Account account,
                              @PathVariable String path,
                              @PathVariable Long boardId,
                              @Valid BoardForm boardForm,
                              Errors errors,
                              Model model,
                              RedirectAttributes redirectAttributes) {

        Study study = studyService.getStudy(path);
        Board board = boardService.getBoardWithComments(boardId);

        if (errors.hasErrors()) {
            model.addAttribute(account);
            model.addAttribute(study);
            model.addAttribute("board", board);
            model.addAttribute("categories", BoardCategory.values());
            model.addAttribute("isManager", study.isManagerOf(account));
            return "board/edit-form";
        }

        boardService.updateBoard(board, account, boardForm);
        redirectAttributes.addFlashAttribute("message", "게시글을 수정했습니다.");

        return "redirect:/study/" + study.getEncodedPath() + "/board/" + boardId;
    }

    // ============================
    // 게시글 삭제
    // ============================

    @DeleteMapping("/{boardId}")
    public String deleteBoard(Account account,
                              @PathVariable String path,
                              @PathVariable Long boardId,
                              RedirectAttributes redirectAttributes) {

        Study study = studyService.getStudy(path);
        Board board = boardService.getBoardWithComments(boardId);

        boardService.deleteBoard(board, account);
        redirectAttributes.addFlashAttribute("message", "게시글을 삭제했습니다.");

        return "redirect:/study/" + study.getEncodedPath() + "/board";
    }

    // ============================
    // 댓글 작성
    // ============================

    @PostMapping("/{boardId}/comments")
    public String addComment(Account account,
                             @PathVariable String path,
                             @PathVariable Long boardId,
                             @Valid BoardCommentForm commentForm,
                             Errors errors,
                             RedirectAttributes redirectAttributes) {

        Study study = studyService.getStudy(path);
        Board board = boardService.getBoardWithComments(boardId);

        if (errors.hasErrors()) {
            redirectAttributes.addFlashAttribute("commentError", "댓글 내용을 입력하세요.");
            return "redirect:/study/" + study.getEncodedPath() + "/board/" + boardId;
        }

        boardService.addComment(board, account, commentForm);

        return "redirect:/study/" + study.getEncodedPath() + "/board/" + boardId;
    }

    // ============================
    // 댓글 삭제
    // ============================

    @DeleteMapping("/{boardId}/comments/{commentId}")
    public String deleteComment(Account account,
                                @PathVariable String path,
                                @PathVariable Long boardId,
                                @PathVariable Long commentId,
                                RedirectAttributes redirectAttributes) {

        Study study = studyService.getStudy(path);
        BoardComment comment = boardCommentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("댓글을 찾을 수 없습니다."));

        boardService.deleteComment(comment, account);
        redirectAttributes.addFlashAttribute("message", "댓글을 삭제했습니다.");

        return "redirect:/study/" + study.getEncodedPath() + "/board/" + boardId;
    }
}