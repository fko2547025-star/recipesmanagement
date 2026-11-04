package com.example.recipesmanagement.like;

/**
 * いいねの集計結果1件分（どのレシピに、いいねが何件あるか）。
 *
 * クラスではなくインターフェースで getter だけを書いておくと、
 * Spring Data JPA が @Query の結果を自動でこの形にして返してくれる（プロジェクションと呼ぶ）。
 * getter の名前は、クエリの AS で付けた名前と対応させる。
 *   AS recipeId  → getRecipeId()
 *   AS likeCount → getLikeCount()
 */
public interface RecipeLikeCount {

    Long getRecipeId();

    Long getLikeCount();
}