package com.example.recipesmanagement.ranking;

import com.example.recipesmanagement.recipe.Recipe;

/**
 * ランキングの1行分（順位・レシピ・いいね数）。
 * 画面に渡すためだけのクラスで、DB とは関係ない。
 */
public class RankingItem {

    private final int rank;          // 順位
    private final Recipe recipe;     // レシピ
    private final long likeCount;    // いいね数

    public RankingItem(int rank, Recipe recipe, long likeCount) {
        this.rank = rank;
        this.recipe = recipe;
        this.likeCount = likeCount;
    }

    public int getRank() { return rank; }
    public Recipe getRecipe() { return recipe; }
    public long getLikeCount() { return likeCount; }
}