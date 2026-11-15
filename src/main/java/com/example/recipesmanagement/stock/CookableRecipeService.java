package com.example.recipesmanagement.stock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.recipesmanagement.ingredient.Ingredient;
import com.example.recipesmanagement.recipe.Recipe;
import com.example.recipesmanagement.recipe.RecipeIngredient;
import com.example.recipesmanagement.recipe.RecipeRepository;
import com.example.recipesmanagement.user.AppUser;

/**
 * 今の在庫で作れるレシピを探すクラス。
 * 材料1つずつの判定は、レシピ詳細の在庫チェックと同じ StockCheckService.checkOne を使う。
 */
@Service
public class CookableRecipeService {

    private final RecipeRepository recipeRepository;
    private final StockCheckService stockCheckService;

    public CookableRecipeService(RecipeRepository recipeRepository, StockCheckService stockCheckService) {
        this.recipeRepository = recipeRepository;
        this.stockCheckService = stockCheckService;
    }

    /**
     * 足りない材料が maxMissing 個以下のレシピを、作りやすい順に返す。
     *
     * @param maxMissing 足りない材料がいくつまでなら候補に入れるか（例：2 → あと2品まで）
     */
    @Transactional(readOnly = true)
    public List<CookableRecipe> find(AppUser user, int maxMissing) {
        // 在庫は最初に1回だけ取り出して、全部のレシピで使い回す
        List<Ingredient> stock = stockCheckService.loadStock(user);
        if (stock.isEmpty()) {
            return List.of();   // 在庫が1つも無ければ、探しても意味がない
        }

        List<CookableRecipe> result = new ArrayList<>();
        for (Recipe recipe : recipeRepository.findAllWithIngredients()) {
            List<String> missing = new ArrayList<>();
            int total = 0;

            for (RecipeIngredient need : recipe.getIngredients()) {
                // 少々・適量（分量が無い材料）は数えない
                if (need.getQuantity() == null) {
                    continue;
                }
                total++;
                StockCheckItem item = stockCheckService.checkOne(
                        need.getName(), need.getQuantity(), need.getUnit(), stock);
                // isProblem()：足りない（SHORT）か在庫なし（NONE）。単位違いは「ある」扱い
                if (item.isProblem()) {
                    missing.add(need.getName());
                }
            }

            int have = total - missing.size();
            // 在庫と1つも合わないレシピや、足りないものが多すぎるレシピは候補にしない
            if (have > 0 && missing.size() <= maxMissing) {
                result.add(new CookableRecipe(recipe, missing, have, total));
            }
        }

        // 並び順：足りないものが少ない順 → そろっている材料が多い順 → 新しい順
        result.sort(Comparator.comparingInt(CookableRecipe::getMissingCount)
                .thenComparing(Comparator.comparingInt(CookableRecipe::getHaveCount).reversed())
                .thenComparing(c -> c.getRecipe().getId(), Comparator.reverseOrder()));
        return result;
    }
}