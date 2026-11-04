package com.example.recipesmanagement.stock;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.recipesmanagement.ingredient.Ingredient;
import com.example.recipesmanagement.recipe.Recipe;
import com.example.recipesmanagement.recipe.RecipeRepository;

/**
 * 期限が近い食材を使えるレシピを探すクラス。
 *
 * 期限が近い食材ごとにレシピを探し、
 * 「期限が近い食材をたくさん使えるレシピ」ほど上に並べる。
 */
@Service
public class ExpiringRecipeService {

    // おすすめとして表示する最大件数
    private static final int MAX_SUGGESTIONS = 5;

    private final RecipeRepository recipeRepository;

    public ExpiringRecipeService(RecipeRepository recipeRepository) {
        this.recipeRepository = recipeRepository;
    }

    /**
     * @param expiring 期限が近い食材（ログイン中のユーザーの在庫）
     */
    @Transactional(readOnly = true)
    public List<RecipeSuggestion> suggest(List<Ingredient> expiring) {
        // レシピの id → おすすめ情報（同じレシピをまとめるため）
        Map<Long, RecipeSuggestion> suggestions = new LinkedHashMap<>();
        // 検索済みの名前（豚ロースが2パックあっても、検索は1回だけにする）
        Set<String> searched = new HashSet<>();

        for (Ingredient item : expiring) {
            String keyword = IngredientNameMatcher.normalize(item.getName());
            // Set.add() は、すでに入っていれば false を返す → 検索済みなら飛ばす
            if (keyword.isEmpty() || !searched.add(keyword)) {
                continue;
            }

            for (Recipe recipe : recipeRepository.findByIngredientName(keyword)) {
                suggestions.computeIfAbsent(recipe.getId(), id -> new RecipeSuggestion(recipe))
                           .addUsable(item.getName());
            }
        }

        // 使える食材が多い順に並べて、上位だけ返す
        // Comparator.comparingInt(...).reversed()：数の大きい順
        return suggestions.values().stream()
                .sorted(Comparator.comparingInt(RecipeSuggestion::getUsableCount).reversed())
                .limit(MAX_SUGGESTIONS)
                .toList();
    }
}