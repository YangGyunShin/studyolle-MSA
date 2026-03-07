package com.studyolle.modules.board.event;

import com.studyolle.modules.board.entity.Board;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 게시글 생성 도메인 이벤트
 *
 * [이벤트 흐름]
 *   BoardService.createBoard()
 *     → eventPublisher.publishEvent(new BoardCreatedEvent(board))
 *     → @Async StudyEventListener.handleBoardCreatedEvent()
 *       → 스터디 멤버에게 웹 알림 / 이메일 발송
 */
@Getter
@RequiredArgsConstructor
public class BoardCreatedEvent {

    private final Board board;
}