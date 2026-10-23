package com.example.recipesmanagement.recipe;

import java.text.DecimalFormat;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * レシピの材料を表す Entity（recipe_ingredients テーブルの1行）。
 *
 * 食材管理の Ingredient（自分の家の在庫）とは別物で、
 * こちらは「レシピに書かれている材料と分量」。
 */
@Entity
@Table(name = "recipe_ingredients")
public class RecipeIngredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // どのレシピの材料か（多対一：1つのレシピがたくさんの材料を持つ）
    // こちら側に recipe_id 列があるので、関係の「本体」はこちら
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    // 材料名（例：豚ロース）
    private String name;

    // 分量（例：200）。「少々」「適量」のように数字が無い材料もあるので null を許す
    private Double quantity;

    // 単位（例：g、大さじ）
    private String unit;

    // 材料の表示順（1, 2, 3...）
    private Integer sortOrder;

    // ===== getter / setter =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Recipe getRecipe() { return recipe; }
    public void setRecipe(Recipe recipe) { this.recipe = recipe; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Double getQuantity() { return quantity; }
    public void setQuantity(Double quantity) { this.quantity = quantity; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    /**
     * 画面表示用の分量。末尾の .0 を消す（200.0 → 200、0.5 → 0.5）。
     * 分量が無い材料は空文字を返す。
     */
    public String getQuantityText() {
        if (quantity == null) {
            return "";
        }
        return new DecimalFormat("0.###").format(quantity);
    }
}