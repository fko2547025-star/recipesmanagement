package com.example.recipesmanagement.recipe;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.recipesmanagement.user.AppUser;

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

    // id のリストに含まれるレシピをまとめて取得（投稿者も一緒に）。ランキング用
    // SQL のイメージ：... WHERE id IN (?, ?, ?, ...)
    @EntityGraph(attributePaths = "user")
    List<Recipe> findByIdIn(Collection<Long> ids);    

    // ===== マイページ用 =====

    // 自分が投稿したレシピ（新しい順）
    // SQL のイメージ：... WHERE user_id = ? ORDER BY id DESC
    @EntityGraph(attributePaths = "user")
    List<Recipe> findByUserOrderByIdDesc(AppUser user);

    /**
     * 自分がいいねしたレシピ（いいねした日時が新しい順）。
     *
     * RecipeLike（いいね）から l.recipe でレシピをたどり、そのレシピを返す。
     * JOIN FETCH r.user：レシピの投稿者も同じ SQL で一緒に取得する（@EntityGraph の JPQL 版）
     */
    @Query("""
            SELECT r FROM RecipeLike l
            JOIN l.recipe r
            JOIN FETCH r.user
            WHERE l.user = :user
            ORDER BY l.createdAt DESC, l.id DESC
            """)
    List<Recipe> findLikedBy(@Param("user") AppUser user);

    /**
     * 自分がコメントしたレシピ（新しいレシピ順）。
     *
     * 同じレシピに何回コメントしても1回だけ表示したいので、
     * 「自分がコメントしたレシピの id の一覧（サブクエリ）」に含まれるレシピ、という形で探す。
     */
    @Query("""
            SELECT r FROM Recipe r
            JOIN FETCH r.user
            WHERE r.id IN (
                SELECT c.recipe.id FROM Comment c WHERE c.user = :user
            )
            ORDER BY r.id DESC
            """)
    List<Recipe> findCommentedBy(@Param("user") AppUser user);

    /**
     * 材料名でレシピを探す（新しい順）。
     *
     * 在庫チェックと同じく「どちらかがもう一方を含んでいれば同じ食材」とみなす。
     *   ・検索語「豚ロース」で、材料「豚ロース薄切り」が見つかる
     *   ・検索語「豚ロース薄切り」で、材料「豚ロース」が見つかる
     *
     * LOCATE(探す文字, 対象)：対象の中に探す文字があれば、その位置（1以上）を返す。無ければ 0
     * EXISTS (サブクエリ)：条件に合う材料が1つでもあるレシピだけを返す（同じレシピが重複しない）
     */
    @Query("""
            SELECT r FROM Recipe r
            JOIN FETCH r.user
            WHERE EXISTS (
                SELECT 1 FROM RecipeIngredient ri
                WHERE ri.recipe = r
                  AND (LOCATE(:keyword, ri.name) > 0 OR LOCATE(ri.name, :keyword) > 0)
            )
            ORDER BY r.id DESC
            """)
    List<Recipe> findByIngredientName(@Param("keyword") String keyword);
}