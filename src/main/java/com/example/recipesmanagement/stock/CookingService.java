package com.example.recipesmanagement.stock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.recipesmanagement.ingredient.Ingredient;
import com.example.recipesmanagement.ingredient.IngredientRepository;
import com.example.recipesmanagement.recipe.Recipe;
import com.example.recipesmanagement.recipe.RecipeIngredient;
import com.example.recipesmanagement.user.AppUser;

/**
 * 「作った」ときに、どの在庫をどれだけ減らすかを計算するクラス。
 * 計算するだけで、実際に減らすのは CookingLine.Deduction の apply()。
 */
@Service
public class CookingService {

    private final IngredientRepository ingredientRepository;

    public CookingService(IngredientRepository ingredientRepository) {
        this.ingredientRepository = ingredientRepository;
    }

    /**
     * レシピの材料ごとに、減らす在庫を計算する。
     *
     * 呼び出し元（MealPlanService）の @Transactional の中で呼ばれると、
     * そのトランザクションに参加する（別のトランザクションにはならない）。
     */
    @Transactional(readOnly = true)
    public List<CookingLine> plan(Recipe recipe, AppUser user) {
        // 自分の在庫を期限が近い順に（数量0は除く）
        List<Ingredient> stock = ingredientRepository.findByUser(user, Sort.by("expiryDate")).stream()
                .filter(i -> i.getQuantity() != null && i.getQuantity() > 0)
                .toList();

        // 在庫ごとの「残り量」を覚えておく（id → 残り量）
        // レシピに同じ食材が2回出てくる場合（例：タレ用とかけ用の醤油）、
        // 1回目で使った分を2回目で二重に使わないようにするため
        Map<Long, Double> remaining = new HashMap<>();
        for (Ingredient i : stock) {
            remaining.put(i.getId(), i.getQuantity());
        }

        List<CookingLine> lines = new ArrayList<>();
        for (RecipeIngredient need : recipe.getIngredients()) {
            lines.add(planOne(need, stock, remaining));
        }
        return lines;
    }

    /**
     * 材料1つ分の計算。
     */
    private CookingLine planOne(RecipeIngredient need, List<Ingredient> stock, Map<Long, Double> remaining) {
        String unitText = need.getUnit() != null ? need.getUnit() : "";
        String neededText = need.getQuantityText() + unitText;
        if (neededText.isEmpty()) {
            neededText = "-";
        }

        // 名前が合い、まだ残りがある在庫
        List<Ingredient> matched = stock.stream()
                .filter(s -> IngredientNameMatcher.matches(s.getName(), need.getName()))
                .filter(s -> remaining.get(s.getId()) > 0)
                .toList();

        if (matched.isEmpty()) {
            return new CookingLine(need.getName(), neededText, List.of(), "在庫なし");
        }
        if (need.getQuantity() == null) {
            return new CookingLine(need.getName(), neededText, List.of(), "分量が無いので減らしません");
        }

        // 単位が同じ在庫だけ
        String unit = IngredientNameMatcher.unitKey(need.getUnit());
        List<Ingredient> sameUnit = matched.stream()
                .filter(s -> IngredientNameMatcher.unitKey(s.getUnit()).equals(unit))
                .toList();

        if (sameUnit.isEmpty()) {
            return new CookingLine(need.getName(), neededText, List.of(), "単位が違うため減らせません");
        }

        // 期限が近い在庫から順に、必要な量になるまで減らす
        double left = need.getQuantity();   // まだ減らす必要がある量
        List<CookingLine.Deduction> deductions = new ArrayList<>();

        for (Ingredient s : sameUnit) {
            if (left <= 0) {
                break;
            }
            double available = remaining.get(s.getId());
            double take = Math.min(available, left);   // 在庫と必要量の少ないほうだけ減らす

            deductions.add(new CookingLine.Deduction(s, available, take));
            remaining.put(s.getId(), available - take);
            left -= take;
        }

        // 小数の計算誤差（0.30000000000000004 など）で、ほぼ0なのに「足りない」とならないよう、ごく小さい値は無視する
        String note = left > 0.000001
                ? "あと" + CookingLine.format(left) + unitText + "足りません"
                : null;

        return new CookingLine(need.getName(), neededText, deductions, note);
    }
}