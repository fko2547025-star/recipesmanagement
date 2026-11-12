package com.example.recipesmanagement.ingredient;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.recipesmanagement.user.AppUser;

public interface IngredientRepository extends JpaRepository<Ingredient, Long> {

    // このユーザーの食材を全件
    List<Ingredient> findByUser(AppUser user, Sort sort);

    // このユーザーの食材をカテゴリで絞り込み
    List<Ingredient> findByUserAndCategory(AppUser user, String category, Sort sort);

    // このユーザーの食材を1件（他人の食材なら見つからない）
    Optional<Ingredient> findByIdAndUser(Long id, AppUser user);
    /**
     * そのユーザーの「期限が近い食材」の数（ヘッダーのバッジ用）。
     * 期限日が date 以前（期限切れも含む）で、数量が quantity より多い（使い切っていない）もの。
     *
     * メソッド名から SQL が作られる（派生クエリ）：
     *   SELECT COUNT(*) FROM ingredients i JOIN users u ON i.user_id = u.id
     *   WHERE u.username = ? AND i.expiry_date <= ? AND i.quantity > ?
     */
    long countByUser_UsernameAndExpiryDateLessThanEqualAndQuantityGreaterThan(
        String username, LocalDate date, Double quantity);
}