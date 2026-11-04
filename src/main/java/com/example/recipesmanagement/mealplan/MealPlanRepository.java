package com.example.recipesmanagement.mealplan;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

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
}