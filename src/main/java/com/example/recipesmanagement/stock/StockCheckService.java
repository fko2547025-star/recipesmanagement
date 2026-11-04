package com.example.recipesmanagement.stock;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.recipesmanagement.ingredient.Ingredient;
import com.example.recipesmanagement.ingredient.IngredientRepository;
import com.example.recipesmanagement.recipe.Recipe;
import com.example.recipesmanagement.recipe.RecipeIngredient;
import com.example.recipesmanagement.user.AppUser;

/**
 * レシピの材料と、ユーザーの在庫（食材管理）を照らし合わせるクラス。
 */
@Service
public class StockCheckService {

    // 期限がこの日数以内なら「期限間近」
    public static final int EXPIRING_DAYS = 3;

    private final IngredientRepository ingredientRepository;

    public StockCheckService(IngredientRepository ingredientRepository) {
        this.ingredientRepository = ingredientRepository;
    }

    /**
     * 自分の在庫を、期限が近い順に全部取得する（数量0は除く）。
     */
    @Transactional(readOnly = true)
    public List<Ingredient> loadStock(AppUser user) {
        return ingredientRepository.findByUser(user, Sort.by("expiryDate")).stream()
                .filter(i -> i.getQuantity() != null && i.getQuantity() > 0)
                .toList();
    }

    /**
     * 材料1つ分をチェックする。
     *
     * @param stock loadStock() で取得した在庫
     */
    public StockCheckItem checkOne(String name, Double quantity, String unit, List<Ingredient> stock) {
        List<Ingredient> matched = stock.stream()
                .filter(s -> IngredientNameMatcher.matches(s.getName(), name))
                .toList();
        return StockCheckItem.of(name, quantity, unit, matched, LocalDate.now().plusDays(EXPIRING_DAYS));
    }

    /**
     * レシピの材料ごとに、在庫をチェックする（レシピ詳細用）。
     */
    @Transactional(readOnly = true)
    public List<StockCheckItem> check(Recipe recipe, AppUser user) {
        List<Ingredient> stock = loadStock(user);

        List<StockCheckItem> result = new ArrayList<>();
        for (RecipeIngredient need : recipe.getIngredients()) {
            result.add(checkOne(need.getName(), need.getQuantity(), need.getUnit(), stock));
        }
        return result;
    }
}