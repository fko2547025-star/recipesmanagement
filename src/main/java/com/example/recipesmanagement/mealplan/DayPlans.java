package com.example.recipesmanagement.mealplan;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 献立の週表示の1日分（日付と、朝・昼・夜ごとの献立）。
 * 画面に渡すためだけのクラスで、DB とは関係ない（Entity ではない）。
 */
public class DayPlans {

    private final LocalDate date;     // 日付
    private final boolean today;      // 今日かどうか（列の色を変えるため）

    // 「朝 → 献立のリスト」「昼 → …」「夜 → …」の順に入っている
    // LinkedHashMap：入れた順番を覚えている Map（朝・昼・夜の順番で表示したいので）
    private final Map<String, List<MealPlan>> slots = new LinkedHashMap<>();

    public DayPlans(LocalDate date, boolean today) {
        this.date = date;
        this.today = today;
        // 献立が無い区分も、空のリストで先に用意しておく（画面で必ず 朝・昼・夜 の3つの枠を出すため）
        for (String mealType : MealPlan.MEAL_TYPES) {
            slots.put(mealType, new ArrayList<>());
        }
    }

    /**
     * 献立を、その区分（朝・昼・夜）の枠に入れる。
     */
    public void add(MealPlan plan) {
        slots.get(plan.getMealType()).add(plan);
    }

    public LocalDate getDate() { return date; }
    public boolean isToday() { return today; }
    public Map<String, List<MealPlan>> getSlots() { return slots; }
}