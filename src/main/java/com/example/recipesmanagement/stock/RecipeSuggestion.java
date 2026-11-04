package com.example.recipesmanagement.stock;

import java.util.LinkedHashSet;
import java.util.Set;

import com.example.recipesmanagement.recipe.Recipe;

/**
 * 期限が近い食材を使えるレシピ1件分。
 * 画面に渡すためだけのクラスで、DB とは関係ない。
 */
public class RecipeSuggestion {

    private final Recipe recipe;
    // このレシピで使える期限間近の食材の名前（重複なし・見つかった順）
    private final Set<String> usableNames = new LinkedHashSet<>();

    public RecipeSuggestion(Recipe recipe) {
        this.recipe = recipe;
    }

    public void addUsable(String name) {
        usableNames.add(name);
    }

    public Recipe getRecipe() { return recipe; }

    // 使える食材の数（多いほどおすすめ）
    public int getUsableCount() { return usableNames.size(); }

    // 使える食材を「豚ロース、卵」の形で
    public String getUsableText() { return String.join("、", usableNames); }
}