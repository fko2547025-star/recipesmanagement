package com.example.recipesmanagement.mealplan;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.recipesmanagement.ingredient.Ingredient;
import com.example.recipesmanagement.recipe.RecipeIngredient;
import com.example.recipesmanagement.stock.IngredientNameMatcher;
import com.example.recipesmanagement.stock.StockCheckService;
import com.example.recipesmanagement.user.AppUser;

/**
 * 1週間の献立から、買い物リストを作るクラス。
 *
 * 対象：今日以降の、まだ作っていない、レシピのある献立。
 * 同じ材料（名前と単位が同じもの）は、複数のレシピの量を合計してから在庫と比べる。
 */
@Service
public class ShoppingListService {

    private final MealPlanRepository mealPlanRepository;
    private final StockCheckService stockCheckService;

    public ShoppingListService(MealPlanRepository mealPlanRepository, StockCheckService stockCheckService) {
        this.mealPlanRepository = mealPlanRepository;
        this.stockCheckService = stockCheckService;
    }

    /**
     * 買い物リストを作る。
     *
     * @param monday 週の始まり（月曜日）
     */
    @Transactional(readOnly = true)
    public List<ShoppingItem> build(AppUser user, LocalDate monday) {
        List<MealPlan> plans = targetPlans(user, monday);

        // ----- 1. 材料を集めて合計する -----
        // キー：「名前（空白を除いたもの）|単位」。名前と単位が同じ材料を1つにまとめる
        Map<String, Need> needs = new LinkedHashMap<>();

        for (MealPlan plan : plans) {
            // 例：豚の生姜焼き（10/30）
            String label = plan.getRecipe().getTitle()
                    + "（" + plan.getPlanDate().getMonthValue() + "/" + plan.getPlanDate().getDayOfMonth() + "）";

            for (RecipeIngredient ri : plan.getRecipe().getIngredients()) {
                String key = IngredientNameMatcher.normalize(ri.getName())
                        + "|" + IngredientNameMatcher.unitKey(ri.getUnit());

                // computeIfAbsent：キーが無ければ新しく作って入れ、あればそれを返す
                Need need = needs.computeIfAbsent(key, k -> new Need(ri.getName(), ri.getUnit()));
                need.add(ri.getQuantity(), label);
            }
        }

        // ----- 2. 合計した量を在庫と比べる -----
        List<Ingredient> stock = stockCheckService.loadStock(user);

        List<ShoppingItem> items = new ArrayList<>();
        for (Need need : needs.values()) {
            items.add(new ShoppingItem(
                    stockCheckService.checkOne(need.name, need.quantity, need.unit, stock),
                    String.join("、", need.usedIn)));
        }
        return items;
    }

    /**
     * 対象の献立（今日以降・まだ作っていない・レシピあり）。
     */
    public List<MealPlan> targetPlans(AppUser user, LocalDate monday) {
        LocalDate today = LocalDate.now();
        LocalDate sunday = monday.plusDays(6);

        // 今週なら今日から、来週以降なら月曜から
        LocalDate from = monday.isBefore(today) ? today : monday;
        if (from.isAfter(sunday)) {
            return List.of();   // 過ぎた週は対象なし
        }

        return mealPlanRepository.findByUserAndPlanDateBetween(user, from, sunday, Sort.by("planDate", "planTime"))
                .stream()
                .filter(p -> p.getRecipe() != null && !p.isCooked())
                .toList();
    }

    /**
     * 集計中の材料1つ分（このクラスの中だけで使う）。
     */
    private static class Need {
        private final String name;
        private final String unit;
        private Double quantity;                                // 合計した量（量のある材料が1つも無ければ null）
        private final Set<String> usedIn = new LinkedHashSet<>(); // 使う献立（重複なし・入れた順）

        Need(String name, String unit) {
            this.name = name;
            this.unit = unit;
        }

        void add(Double q, String label) {
            if (q != null) {
                quantity = (quantity == null ? 0 : quantity) + q;
            }
            usedIn.add(label);
        }
    }
}