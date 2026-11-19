package com.example.recipesmanagement.report;

import java.time.LocalDateTime;

import com.example.recipesmanagement.comment.Comment;
import com.example.recipesmanagement.recipe.Recipe;
import com.example.recipesmanagement.user.AppUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 通報（reports テーブルの1行）。レシピかコメントのどちらか一方を通報する。
 * 通報されたレシピ・コメントが消えると、通報も一緒に消える（ON DELETE CASCADE）。
 */
@Entity
@Table(name = "reports")
public class Report {

    // 理由の最大文字数（テーブルの VARCHAR(500) に合わせる）
    public static final int MAX_REASON = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 通報した人
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    private AppUser reporter;

    // 通報されたレシピ（コメントの通報なら null）
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id")
    private Recipe recipe;

    // 通報されたコメント（レシピの通報なら null）
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id")
    private Comment comment;

    private String reason;

    // 通報した日時（DB が自動で入れる）
    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }

    public AppUser getReporter() { return reporter; }
    public void setReporter(AppUser reporter) { this.reporter = reporter; }

    public Recipe getRecipe() { return recipe; }
    public void setRecipe(Recipe recipe) { this.recipe = recipe; }

    public Comment getComment() { return comment; }
    public void setComment(Comment comment) { this.comment = comment; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
