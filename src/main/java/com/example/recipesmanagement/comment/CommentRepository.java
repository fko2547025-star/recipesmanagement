package com.example.recipesmanagement.comment;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.recipesmanagement.recipe.Recipe;
import com.example.recipesmanagement.user.AppUser;

/**
 * comments テーブルとのやりとりを担当する Repository。
 */
public interface CommentRepository extends JpaRepository<Comment, Long> {

    // このレシピのコメントを古い順に（コメントした人も一緒に取得）
    @EntityGraph(attributePaths = "user")
    List<Comment> findByRecipeOrderByIdAsc(Recipe recipe);

    // コメントを1件（コメントした人とレシピも一緒に取得）。編集・削除用
    @EntityGraph(attributePaths = {"user", "recipe"})
    Optional<Comment> findWithDetailsById(Long id);

    long countByUser(AppUser user);
}