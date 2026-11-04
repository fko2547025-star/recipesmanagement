package com.example.recipesmanagement.like;

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
 * いいねを表す Entity（likes テーブルの1行）。
 *
 * クラス名を Like にしないのは、LIKE が SQL（と JPA の検索言語）の予約語で、
 * 内部で作られる検索文がエラーになることがあるため。
 *
 * 1人が同じレシピに2回いいねできないよう、テーブル側で UNIQUE (user_id, recipe_id) にしている。
 * 日時を1件ずつ記録しているのは、週間・月間ランキングで「いつのいいねか」を数えるため。
 */
@Entity
@Table(name = "likes")
public class RecipeLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // いいねした人
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    // いいねされたレシピ
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    // いいねした日時（DB が自動で入れる）
    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }

    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }

    public Recipe getRecipe() { return recipe; }
    public void setRecipe(Recipe recipe) { this.recipe = recipe; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}