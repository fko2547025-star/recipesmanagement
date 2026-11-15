package com.example.recipesmanagement.stock;

import java.util.List;

import com.example.recipesmanagement.recipe.Recipe;

/**
 * 「在庫で作れるレシピ」の1件分（レシピと、足りない材料）。
 * 画面に渡すためだけのクラスで、DB とは関係ない。
 */
public class CookableRecipe {

    private final Recipe recipe;
    private final List<String> missingNames;   // 足りない材料の名前
    private final int haveCount;               // そろっている材料の数
    private final int totalCount;              // 判定に使った材料の数（少々・適量は除く）

    public CookableRecipe(Recipe recipe, List<String> missingNames, int haveCount, int totalCount) {
        this.recipe = recipe;
        this.missingNames = missingNames;
        this.haveCount = haveCount;
        this.totalCount = totalCount;
    }

    public Recipe getRecipe() { return recipe; }
    public List<String> getMissingNames() { return missingNames; }
    public int getMissingCount() { return missingNames.size(); }
    public int getHaveCount() { return haveCount; }
    public int getTotalCount() { return totalCount; }

    /** 足りないものが無い ＝ 今すぐ作れる */
    public boolean isReady() { return missingNames.isEmpty(); }
}