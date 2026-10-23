package com.example.recipesmanagement.recipe;

import java.util.ArrayList;
import java.util.List;

/**
 * レシピ投稿・編集フォームの入力値を受け取るクラス（フォーム専用の入れ物）。
 *
 * 材料は、画面の各行の入力欄が同じ name を持っているので、送信すると上の行から順に List に入る。
 * 3つの List の同じ番号が、同じ行の「名前・分量・単位」になる。
 *
 * 入力チェック（validate）もこのクラスに持たせている。
 */
public class RecipeForm {

    private String title;          // タイトル
    private String category;       // カテゴリ
    private String description;    // 説明（任意）
    private String steps;          // 作り方

    // 材料（行ごとの入力値）。分量は空欄や数字以外もありえるので String で受け取る
    private List<String> ingredientNames = new ArrayList<>();
    private List<String> ingredientQuantities = new ArrayList<>();
    private List<String> ingredientUnits = new ArrayList<>();

    /**
     * 既存のレシピの内容を詰めたフォームを作る（編集画面用）。
     * static メソッドなので、RecipeForm.from(recipe) のように呼び出す。
     */
    public static RecipeForm from(Recipe recipe) {
        RecipeForm form = new RecipeForm();
        form.setTitle(recipe.getTitle());
        form.setCategory(recipe.getCategory());
        form.setDescription(recipe.getDescription());
        form.setSteps(recipe.getSteps());

        for (RecipeIngredient ing : recipe.getIngredients()) {
            form.ingredientNames.add(ing.getName());
            form.ingredientQuantities.add(ing.getQuantityText());   // 200.0 ではなく 200
            form.ingredientUnits.add(ing.getUnit() != null ? ing.getUnit() : "");
        }
        return form;
    }

    /**
     * 材料の3つの List を同じ長さにそろえ、最低 minRows 行あるようにする。
     */
    public void ensureRows(int minRows) {
        int size = Math.max(minRows,
                Math.max(ingredientNames.size(),
                        Math.max(ingredientQuantities.size(), ingredientUnits.size())));
        while (ingredientNames.size() < size) ingredientNames.add("");
        while (ingredientQuantities.size() < size) ingredientQuantities.add("");
        while (ingredientUnits.size() < size) ingredientUnits.add("");
    }

    /**
     * 入力チェック。問題があればエラーメッセージのリストを返す（無ければ空のリスト）。
     *
     * HTML の required や maxlength はブラウザ側のチェックで簡単に外せるので、
     * サーバー側のこのチェックが本当の守りになる。
     */
    public List<String> validate() {
        List<String> errors = new ArrayList<>();

        if (isBlank(title)) {
            errors.add("タイトルを入力してください");
        } else if (title.trim().length() > 100) {
            errors.add("タイトルは100文字以内で入力してください");
        }

        // 画面のプルダウン以外の値が送られてきても受け付けない
        if (!Recipe.CATEGORIES.contains(category)) {
            errors.add("カテゴリを選んでください");
        }

        if (description != null && description.length() > 500) {
            errors.add("説明は500文字以内で入力してください");
        }

        if (isBlank(steps)) {
            errors.add("作り方を入力してください");
        } else if (steps.length() > 4000) {
            errors.add("作り方は4000文字以内で入力してください");
        }

        // 材料：名前のある行が1つ以上あるか、分量が数字になっているか
        boolean hasIngredient = false;
        for (int i = 0; i < ingredientNames.size(); i++) {
            String name = ingredientNames.get(i);
            if (isBlank(name)) {
                continue;
            }
            hasIngredient = true;

            String quantity = i < ingredientQuantities.size() ? ingredientQuantities.get(i) : null;
            if (!isBlank(quantity)) {
                try {
                    Double.parseDouble(quantity.trim());
                } catch (NumberFormatException e) {
                    errors.add("「" + name.trim() + "」の分量は数字で入力してください（例：0.5）");
                }
            }
        }
        if (!hasIngredient) {
            errors.add("材料を1つ以上入力してください");
        }

        return errors;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    // ===== getter / setter =====

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSteps() { return steps; }
    public void setSteps(String steps) { this.steps = steps; }

    public List<String> getIngredientNames() { return ingredientNames; }
    public void setIngredientNames(List<String> ingredientNames) { this.ingredientNames = ingredientNames; }

    public List<String> getIngredientQuantities() { return ingredientQuantities; }
    public void setIngredientQuantities(List<String> ingredientQuantities) { this.ingredientQuantities = ingredientQuantities; }

    public List<String> getIngredientUnits() { return ingredientUnits; }
    public void setIngredientUnits(List<String> ingredientUnits) { this.ingredientUnits = ingredientUnits; }
}