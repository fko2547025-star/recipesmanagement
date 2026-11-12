package com.example.recipesmanagement.mealplan;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;


import com.example.recipesmanagement.recipe.Recipe;
import com.example.recipesmanagement.user.AppUser;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 献立を表す Entity（meal_plans テーブルの1行）。
 *
 * レシピとメモはどちらも任意だが、どちらか1つは必要（MealPlanForm で入力チェックする）。
 * レシピが削除されると、テーブル作成時の ON DELETE SET NULL で recipe が空になる。
 */
@Entity
@Table(name = "meal_plans")
public class MealPlan {

    public static final List<String> MEAL_TYPES = List.of("朝","昼","夜");

    public static final Map<String, String> MEAL_ICONS = Map.of(
        "朝", "wb_twilight",
        "昼", "wb_sunny",
        "夜", "dark_mode"
    );

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // この献立の持ち主
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    // 日付（必須）
    private LocalDate planDate;

    private String mealType;

    // レシピ（任意）。nullable = true なので空でもよい
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id")
    private Recipe recipe;

    // メモ（任意。例：外食、カレーの残り）
    private String memo;

    // 作ったかどうか（「作った」で在庫を減らしたら true）
    private boolean cooked;

    // ===== getter / setter =====

    public Long getId() { return id; }

    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }

    public LocalDate getPlanDate() { return planDate; }
    public void setPlanDate(LocalDate planDate) { this.planDate = planDate; }

    public String getMealType() { return mealType; }
    public void setMealType(String mealType) { this.mealType = mealType; }

    public String getMealIcon() {
        return MEAL_ICONS.getOrDefault(mealType, "restaurant");
    }

    public String getDisplayName() {
        if (recipe != null) {
            return recipe.getTitle();
        }
        if (memo != null) {
            return memo;
        }
        return "(レシピが削除されました)";
    }

    public Recipe getRecipe() { return recipe; }
    public void setRecipe(Recipe recipe) { this.recipe = recipe; }

    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }

    public boolean isCooked() { return cooked; }
    public void setCooked(boolean cooked) { this.cooked = cooked; }
}