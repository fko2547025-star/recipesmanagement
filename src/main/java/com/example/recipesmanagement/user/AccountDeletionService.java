package com.example.recipesmanagement.user;

import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 退会：ユーザーと、そのユーザーのデータをすべて消す。
 *
 * テーブルの ON DELETE CASCADE の有無に頼らず、消す順番をここで決めて SQL で消す
 * （ほかのテーブルから参照されている行は、先に参照している側を消さないと消せないため）。
 * 1つのトランザクションなので、途中で失敗したら全部元に戻る。
 */
@Service
public class AccountDeletionService {

    private final JdbcTemplate jdbc;

    public AccountDeletionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * ユーザーのデータを消す。
     *
     * @return 消したデータが使っていた写真のファイル名（データベースを消し終わってから、呼び出す側でファイルを消す）
     */
    @Transactional
    public List<String> delete(Long userId) {
        // このユーザーのレシピ（ほかの人のいいね・コメント・献立から参照されている）
        String myRecipes = "SELECT id FROM recipes WHERE user_id = ?";

        // 消す前に、写真のファイル名を集めておく
        List<String> images = new ArrayList<>();
        images.addAll(jdbc.queryForList(
                "SELECT image_path FROM recipes WHERE user_id = ? AND image_path IS NOT NULL", String.class, userId));
        images.addAll(jdbc.queryForList(
                "SELECT image_path FROM comments WHERE image_path IS NOT NULL AND (user_id = ? OR recipe_id IN (" + myRecipes + "))",
                String.class, userId, userId));
        images.addAll(jdbc.queryForList(
                "SELECT avatar_path FROM users WHERE id = ? AND avatar_path IS NOT NULL", String.class, userId));

        // 通報（自分がした通報・自分のレシピやコメントへの通報）
        jdbc.update("DELETE FROM reports WHERE reporter_id = ?"
                + " OR recipe_id IN (" + myRecipes + ")"
                + " OR comment_id IN (SELECT id FROM comments WHERE user_id = ? OR recipe_id IN (" + myRecipes + "))",
                userId, userId, userId, userId);
        // いいね・コメント（自分がしたもの・自分のレシピについたもの）
        jdbc.update("DELETE FROM likes WHERE user_id = ? OR recipe_id IN (" + myRecipes + ")", userId, userId);
        jdbc.update("DELETE FROM comments WHERE user_id = ? OR recipe_id IN (" + myRecipes + ")", userId, userId);
        // 献立：自分の献立は消す。ほかの人の献立に入っている自分のレシピは、レシピの欄だけ空にする
        jdbc.update("DELETE FROM meal_plans WHERE user_id = ?", userId);
        jdbc.update("UPDATE meal_plans SET recipe_id = NULL WHERE recipe_id IN (" + myRecipes + ")", userId);
        // レシピ（材料を先に）
        jdbc.update("DELETE FROM recipe_ingredients WHERE recipe_id IN (" + myRecipes + ")", userId);
        jdbc.update("DELETE FROM recipes WHERE user_id = ?", userId);
        // 食材・フォロー・パスワード再設定のリンク
        jdbc.update("DELETE FROM ingredients WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM follows WHERE follower_id = ? OR followee_id = ?", userId, userId);
        jdbc.update("DELETE FROM password_reset_tokens WHERE user_id = ?", userId);
        // 最後にユーザー
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
        return images;
    }
}
