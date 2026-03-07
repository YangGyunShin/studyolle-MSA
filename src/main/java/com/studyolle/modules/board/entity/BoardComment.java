package com.studyolle.modules.board.entity;

import com.studyolle.modules.account.entity.Account;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * BoardComment 엔티티 - 게시글에 달린 댓글을 표현하는 도메인 모델
 *
 * =============================================
 * 설계 의도
 * =============================================
 *
 * 1차 구현에서는 단일 레벨 댓글만 지원합니다.
 * 대댓글(nested comment) 구조는 향후 확장 시 parentComment 필드를 추가하여 구현 가능합니다.
 *
 * [엔티티 연관관계 구조]
 *
 *   BoardComment --> Board    (N:1) 이 댓글이 달린 게시글
 *   BoardComment --> Account  (N:1) 댓글 작성자
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class BoardComment {

    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Board board;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Account account;

    @Column(nullable = false, length = 500)
    private String content;

    @Column(nullable = false)
    private LocalDateTime createdDateTime;

    /**
     * 특정 사용자가 이 댓글을 삭제할 수 있는지 확인합니다.
     * 댓글 작성자 본인 또는 게시글이 속한 스터디의 관리자만 가능합니다.
     */
    public boolean canDelete(Account account) {
        return this.account.equals(account) || this.board.getStudy().isManagerOf(account);
    }
}