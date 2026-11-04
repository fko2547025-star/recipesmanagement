package com.example.recipesmanagement.like;

import java.util.Optional;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.recipesmanagement.recipe.Recipe;
import com.example.recipesmanagement.user.AppUser;

/**
 * likes テーブルとのやりとりを担当する Repository。
 */
public interface LikeRepository extends JpaRepository<RecipeLike, Long> {

    // このユーザーがこのレシピにしたいいねを1件
    // SQL のイメージ：WHERE user_id = ? AND recipe_id = ?
    Optional<RecipeLike> findByUserAndRecipe(AppUser user, Recipe recipe);

    // このユーザーがこのレシピにいいね済みか
    boolean existsByUserAndRecipe(AppUser user, Recipe recipe);

    // このレシピのいいね数
    // countBy〜：件数を数える。SQL のイメージ：SELECT COUNT(*) FROM likes WHERE recipe_id = ?
    long countByRecipe(Recipe recipe);

    /**
     * since 以降に付いたいいねを、レシピごとに数えて多い順に返す（ランキング用）。
     *
     * @param since    この日時以降のいいねだけを数える
     * @param pageable 何件取るか（上位10件など）。クエリの LIMIT に当たる
     */
    @Query("""
            SELECT l.recipe.id AS recipeId, COUNT(l) AS likeCount
            FROM RecipeLike l
            WHERE l.createdAt >= :since
            GROUP BY l.recipe.id
            ORDER BY COUNT(l) DESC, l.recipe.id DESC
            """)
    List<RecipeLikeCount> countLikesSince(@Param("since") LocalDateTime since, Pageable pageable);    
}