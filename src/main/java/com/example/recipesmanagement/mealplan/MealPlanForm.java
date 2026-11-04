package com.example.recipesmanagement.mealplan;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;

/**
 * 献立の登録・編集フォームの入力値を受け取るクラス。
 *
 * Entity（MealPlan）に直接詰めないのは、画面からはレシピを「id の数字」で受け取るため。
 * Service で id からレシピを探して、MealPlan にセットする。
 */
public class MealPlanForm {

    // 日付。<input type="date"> から "2026-10-27" の形で届く
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate planDate;

    // 時間。<input type="time"> から "19:00" の形で届くので、その形を pattern で指定する
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime planTime;

    // 選んだレシピの id（選ばなければ null）
    private Long recipeId;

    // メモ
    private String memo;

    /**
     * 既存の献立の内容を詰めたフォームを作る（編集画面用）。
     */
    public static MealPlanForm from(MealPlan plan) {
        MealPlanForm form = new MealPlanForm();
        form.setPlanDate(plan.getPlanDate());
        form.setPlanTime(plan.getPlanTime());
        form.setRecipeId(plan.getRecipe() != null ? plan.getRecipe().getId() : null);
        form.setMemo(plan.getMemo());
        return form;
    }

    /**
     * 入力チェック。問題があればエラーメッセージのリストを返す（無ければ空のリスト）。
     */
    public List<String> validate() {
        List<String> errors = new ArrayList<>();

        if (planDate == null) {
            errors.add("日付を選んでください");
        }
        // レシピもメモも無い献立は意味が無いので、どちらかを必須にする
        if (recipeId == null && (memo == null || memo.isBlank())) {
            errors.add("レシピを選ぶか、メモを入力してください");
        }
        if (memo != null && memo.trim().length() > 100) {
            errors.add("メモは100文字以内で入力してください");
        }
        return errors;
    }

    // ===== getter / setter =====

    public LocalDate getPlanDate() { return planDate; }
    public void setPlanDate(LocalDate planDate) { this.planDate = planDate; }

    public LocalTime getPlanTime() { return planTime; }
    public void setPlanTime(LocalTime planTime) { this.planTime = planTime; }

    public Long getRecipeId() { return recipeId; }
    public void setRecipeId(Long recipeId) { this.recipeId = recipeId; }

    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }
}