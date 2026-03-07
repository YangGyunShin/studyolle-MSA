package com.studyolle.modules.board.entity;

/**
 * 게시판 카테고리를 정의하는 Enum
 *
 * =============================================
 * [설계 의도]
 * =============================================
 *
 * 스터디 내 게시판은 용도에 따라 4가지 카테고리로 분류됩니다.
 * 각 카테고리는 작성 권한과 표시 방식이 다릅니다:
 *
 *   - NOTICE: 공지사항 (관리자만 작성 가능)
 *   - RESOURCE: 자료 공유 (모든 멤버 작성 가능)
 *   - FREE: 자유 게시판 (모든 멤버 작성 가능)
 *   - QNA: Q&A (모든 멤버 작성 가능)
 *
 * [권한 체크 흐름]
 *
 *   BoardService.createBoard() 에서:
 *   if (category == NOTICE && !study.isManagerOf(account)) {
 *       throw new AccessDeniedException("공지사항은 관리자만 작성할 수 있습니다.");
 *   }
 *
 * [UI에서의 활용]
 *
 *   게시판 목록 페이지에서 카테고리별 필터 탭으로 사용됩니다.
 *   getDisplayName()으로 한글 표시명을 가져옵니다.
 */
public enum BoardCategory {

    NOTICE("공지사항"),
    RESOURCE("자료 공유"),
    FREE("자유 게시판"),
    QNA("Q&A");

    private final String displayName;

    BoardCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}