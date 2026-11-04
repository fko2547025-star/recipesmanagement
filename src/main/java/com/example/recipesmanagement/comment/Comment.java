package com.example.recipesmanagement.comment;

import java.time.LocalDateTime;

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
 * コメントを表す Entity（comments テーブルの1行）。
 */
@Entity
@Table(name = "comments")
public class Comment {

    // コメントの最大文字数（テーブルの VARCHAR(500) に合わせる）
    public static final int MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // どのレシピへのコメントか
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    // コメントした人
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    // コメントの内容
    private String content;

    // 投稿日時（DB が自動で入れる）
    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;

    // 更新日時（投稿時は DB が入れ、編集したときは Java からセットする）
    @Column(insertable = false)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }

    public Recipe getRecipe() { return recipe; }
    public void setRecipe(Recipe recipe) { this.recipe = recipe; }

    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    /**
     * 編集されたコメントか（画面に「編集済み」と出すため）。
     */
    public boolean isEdited() {
        return updatedAt != null && !updatedAt.equals(createdAt);
    }
}