package com.example.recipesmanagement.mealplan;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.recipesmanagement.user.AppUser;

/**
 * meal_plans テーブルとのやりとりを担当する Repository。
 * 食材管理と同じく、検索はすべて「このユーザーの」献立に限定する。
 */
public interface MealPlanRepository extends JpaRepository<MealPlan, Long> {

    // このユーザーの、from 〜 to の期間の献立（レシピも一緒に取得）
    // Between：SQL の BETWEEN。from と to の日も含む
    // SQL のイメージ：WHERE user_id = ? AND plan_date BETWEEN ? AND ? ORDER BY ...
    @EntityGraph(attributePaths = "recipe")
    List<MealPlan> findByUserAndPlanDateBetween(AppUser user, LocalDate from, LocalDate to, Sort sort);

    // このユーザーの献立を1件（他人の献立なら見つからない）
    @EntityGraph(attributePaths = "recipe")
    Optional<MealPlan> findByIdAndUser(Long id, AppUser user);

    /** 「作った」献立の数（料理レベルのポイント用） */
    long countByUserAndCookedTrue(AppUser user);

    /** 期間内の「作った」献立（スタンプカード用） */
    List<MealPlan> findByUserAndCookedTrueAndPlanDateBetween(AppUser user, LocalDate from, LocalDate to);

    /**
     * 「作った」献立がある日の数（同じ日に2つ作っても1日）。ごほうびの判定に使う。
     * COUNT(DISTINCT 列)：重複を除いて数える
     */
    @Query("SELECT COUNT(DISTINCT m.planDate) FROM MealPlan m WHERE m.user = :user AND m.cooked = true")
    long countCookedDays(@Param("user") AppUser user);
}