package com.example.recipesmanagement.mealplan;

import java.time.LocalDate;
import java.util.List;

import com.example.recipesmanagement.recipe.Recipe;

/**
 * おまかせで提案した献立1つ分（まだ保存していない）。
 */
public class SuggestedPlan {

    private final LocalDate date;          // 日付
    private final String mealType;         // 朝・昼・夜
    private final Recipe recipe;           // 提案するレシピ
    private final List<String> expiring;   // 使える「期限が近い在庫」の名前
    private final int haveCount;           // 在庫にある材料の数
    private final List<String> missing;    // 在庫に無い材料の名前

    public SuggestedPlan(LocalDate date, String mealType, Recipe recipe,
                         List<String> expiring, int haveCount, List<String> missing) {
        this.date = date;
        this.mealType = mealType;
        this.recipe = recipe;
        this.expiring = expiring;
        this.haveCount = haveCount;
        this.missing = missing;
    }

    public LocalDate getDate() { return date; }
    public String getMealType() { return mealType; }
    public Recipe getRecipe() { return recipe; }
    public List<String> getExpiring() { return expiring; }
    public int getHaveCount() { return haveCount; }
    public List<String> getMissing() { return missing; }

    /** 朝昼夜のアイコン（MealPlan と同じもの） */
    public String getMealIcon() {
        return MealPlan.MEAL_ICONS.get(mealType);
    }

    /**
     * チェックボックスの値。「日付|朝昼夜|レシピid」の1つの文字列にして送り、保存のときに分解する。
     * 例：2026-11-16|夜|12
     */
    public String getValue() {
        return date + "|" + mealType + "|" + recipe.getId();
    }
}