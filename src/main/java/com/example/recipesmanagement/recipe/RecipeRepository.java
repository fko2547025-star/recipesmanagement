package com.example.recipesmanagement.recipe;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * recipes テーブルとのやりとりを担当する Repository。
 *
 * @EntityGraph：つながっているデータ（投稿者・材料）を、最初の SQL でまとめて取ってくる指定。
 * 付けないと、一覧で投稿者名を表示するたびに users テーブルへの SQL が1件ずつ発行されてしまう。
 */
public interface RecipeRepository extends JpaRepository<Recipe, Long> {

    // すべてのレシピを新しい順に（投稿者も一緒に取得）
    // SQL のイメージ：SELECT ... FROM recipes JOIN users ... ORDER BY id DESC
    @EntityGraph(attributePaths = "user")
    List<Recipe> findAllByOrderByIdDesc();

    // カテゴリで絞り込んで新しい順に（投稿者も一緒に取得）
    // SQL のイメージ：... WHERE category = ? ORDER BY id DESC
    @EntityGraph(attributePaths = "user")
    List<Recipe> findByCategoryOrderByIdDesc(String category);

    // レシピを1件（投稿者と材料も一緒に取得）。詳細画面用
    // find と By の間の「WithDetails」は自由に付けられる名前で、SQL には影響しない
    @EntityGraph(attributePaths = {"user", "ingredients"})
    Optional<Recipe> findWithDetailsById(Long id);
}