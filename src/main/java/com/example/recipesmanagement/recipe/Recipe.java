package com.example.recipesmanagement.recipe;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.example.recipesmanagement.user.AppUser;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * レシピを表す Entity（recipes テーブルの1行）。
 *
 * レシピの材料は recipe_ingredients テーブルに分けて保存していて、
 * ingredients フィールドからまとめて取り出せる。
 */
@Entity
@Table(name = "recipes")
public class Recipe {

    /**
     * レシピのカテゴリの一覧。
     * 一覧の絞り込みや、次回作る投稿フォームの選択肢で使い回す。
     * static なフィールドは DB の列としては扱われない。
     */
    public static final List<String> CATEGORIES = List.of("肉", "魚", "野菜", "ご飯・麺", "汁物", "その他");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 投稿者（多対一：1人のユーザーがたくさんのレシピを投稿する）
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    // タイトル（例：豚の生姜焼き）
    private String title;

    // カテゴリ（CATEGORIES のどれか）
    private String category;

    // 説明・ひとこと（任意）
    private String description;

    // 作り方（改行を含む文章）
    private String steps;

    // 写真の保存場所（写真アップロードを作るときに使う。今は空）
    private String imagePath;

    // 投稿日時
    // DB が DEFAULT CURRENT_TIMESTAMP で自動で入れるので、Java からは書き込まない
    // insertable = false：INSERT のときにこの列を送らない
    // updatable = false：UPDATE のときにこの列を変更しない
    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;

    // 更新日時
    // insertable = false：投稿時は DB の DEFAULT CURRENT_TIMESTAMP に任せる
    // 編集したときは Java から現在日時をセットする
    @Column(insertable = false)
    private LocalDateTime updatedAt;    

    // このレシピの材料（1対多：1つのレシピがたくさんの材料を持つ）
    // mappedBy = "recipe"：関係の本体は RecipeIngredient 側の recipe フィールド（recipe_id 列）にある、という意味
    // cascade = ALL：レシピを保存・削除すると、材料も一緒に保存・削除される（次回の投稿で使う）
    // orphanRemoval = true：リストから外した材料は DB からも削除される（次回の編集で使う）
    // @OrderBy：材料を sortOrder の順に並べて取り出す
    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder")
    private List<RecipeIngredient> ingredients = new ArrayList<>();

    // ===== getter / setter =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSteps() { return steps; }
    public void setSteps(String steps) { this.steps = steps; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    // 投稿日時は DB が決めるので getter だけ
    public LocalDateTime getCreatedAt() { return createdAt; }

    public List<RecipeIngredient> getIngredients() { return ingredients; }
    public void setIngredients(List<RecipeIngredient> ingredients) { this.ingredients = ingredients; }

    /**
     * 材料をこのレシピに追加する。
     *
     * レシピ → 材料（ingredients リスト）と、材料 → レシピ（recipe フィールド）の
     * 両方向の関係を、ここで同時にセットする。
     * 片方だけだと、保存したときに recipe_id が空になってしまう。
     */
    public void addIngredient(RecipeIngredient ingredient) {
        ingredients.add(ingredient);      // レシピ側のリストに追加
        ingredient.setRecipe(this);       // 材料側に「自分はこのレシピの材料」とセット
    }

        public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}