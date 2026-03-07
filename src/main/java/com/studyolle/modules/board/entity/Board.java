package com.studyolle.modules.board.entity;

import com.studyolle.modules.account.entity.Account;
import com.studyolle.modules.study.entity.Study;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Board 엔티티 - 스터디 내 게시글을 표현하는 도메인 모델
 *
 * =============================================
 * 핵심 책임
 * =============================================
 *
 * - 게시글 생성 및 기본 정보 관리 (제목, 내용, 카테고리)
 * - 작성자(Account)와 스터디(Study) 연관관계 관리
 * - 댓글(BoardComment) 목록 관리 (1:N 양방향)
 * - 작성/수정 권한 검증 비즈니스 로직
 *
 * =============================================
 * 엔티티 연관관계 구조
 * =============================================
 *
 *   Board --> Study         (N:1) 이 게시글이 속한 스터디
 *   Board --> Account       (N:1) 작성자
 *   Board <-- BoardComment  (1:N) 댓글 목록
 *
 * =============================================
 * @NamedEntityGraph 전략
 * =============================================
 *
 * Board.withComments: 게시글 상세 페이지에서 댓글까지 한 번에 로딩
 *   → BoardComment의 account(작성자)까지 subgraph로 포함하여 N+1 방지
 *
 * 게시글 목록에서는 댓글 본문이 필요 없으므로 EntityGraph 없이 조회하고,
 * 댓글 수는 commentCount 비정규화 필드로 해결합니다.
 */
@NamedEntityGraph(
        name = "Board.withComments",
        attributeNodes = {
                @NamedAttributeNode(value = "comments", subgraph = "comments-with-account"),
                @NamedAttributeNode("createdBy")
        },
        subgraphs = @NamedSubgraph(
                name = "comments-with-account",
                attributeNodes = @NamedAttributeNode("account")
        )
)
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Board {

        @Id
        @GeneratedValue
        private Long id;

        // ============================
        // 연관 엔티티
        // ============================

        /**
         * 이 게시글이 속한 스터디 - Board : Study = N : 1
         */
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(nullable = false)
        private Study study;

        /**
         * 게시글 작성자 - Board : Account = N : 1
         */
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(nullable = false)
        private Account createdBy;

        /**
         * 댓글 목록 - Board : BoardComment = 1 : N (양방향)
         *
         * - mappedBy = "board": FK의 주인은 BoardComment 쪽
         * - cascade = CascadeType.ALL: 게시글 저장/삭제 시 댓글도 함께 처리
         * - orphanRemoval = true: 댓글이 게시글에서 제거되면 DB에서도 삭제
         * - @OrderBy("createdDateTime"): 작성 시각 순으로 정렬
         */
        @OneToMany(mappedBy = "board", cascade = CascadeType.ALL, orphanRemoval = true)
        @OrderBy("createdDateTime")
        @Builder.Default
        private List<BoardComment> comments = new ArrayList<>();

        // ============================
        // 게시글 기본 정보
        // ============================

        @Column(nullable = false, length = 100)
        private String title;

        @Lob
        @Column(nullable = false)
        private String content;

        @Enumerated(EnumType.STRING)
        @Column(nullable = false)
        private BoardCategory category;

        @Column(nullable = false)
        private LocalDateTime createdDateTime;

        private LocalDateTime modifiedDateTime;

        /**
         * 댓글 수 (비정규화 필드, 조회 성능 최적화)
         */
        @Builder.Default
        private int commentCount = 0;

        // ============================
        // 비즈니스 로직
        // ============================

        /**
         * 특정 사용자가 이 게시글을 수정/삭제할 수 있는지 확인합니다.
         * 작성자 본인 또는 스터디 관리자만 가능합니다.
         */
        public boolean canModify(Account account) {
                return this.createdBy.equals(account) || this.study.isManagerOf(account);
        }

        public boolean isAuthor(Account account) {
                return this.createdBy.equals(account);
        }

        public void increaseCommentCount() {
                this.commentCount++;
        }

        public void decreaseCommentCount() {
                if (this.commentCount > 0) {
                        this.commentCount--;
                }
        }
}