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
    private List<String> steps = new ArrayList<>();   // 作り方（行ごとの入力値）
    private Integer servings;      // 何人分
    private String memo;           // メモ（任意）

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
        form.setSteps(new ArrayList<>(recipe.getStepList()));
        form.setServings(recipe.getServings());
        form.setMemo(recipe.getMemo());
        for (RecipeIngredient ing : recipe.getIngredients()) {
            form.ingredientNames.add(ing.getName());
            form.ingredientQuantities.add(ing.getQuantityText());   // 200.0 ではなく 200
            form.ingredientUnits.add(ing.getUnit() != null ? ing.getUnit() : "");
        }
        return form;
    }

    /**
     * 材料の3つの List を同じ長さにそろえ、
     * 材料は最低 minIngredients 行、作り方は最低 minSteps 行あるようにする。
     */
    public void ensureRows(int minIngredients, int minSteps) {
        int size = Math.max(minIngredients,
                Math.max(ingredientNames.size(),
                        Math.max(ingredientQuantities.size(), ingredientUnits.size())));
        while (ingredientNames.size() < size) ingredientNames.add("");
        while (ingredientQuantities.size() < size) ingredientQuantities.add("");
        while (ingredientUnits.size() < size) ingredientUnits.add("");

        // 作り方：足りない分だけ空の行を足す
        while (steps.size() < minSteps) steps.add("");
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

        if (category == null || !Recipe.CATEGORIES.contains(category)) {
            errors.add("カテゴリを選んでください");
        }

        if (description != null && description.length() > 500) {
            errors.add("説明は500文字以内で入力してください");
        }
        // 作り方：空でない手順が1つ以上あるか。
        // DB には改行でつないで保存するので、つないだ合計がテーブルの上限（4000文字）以内か
        List<String> filledSteps = steps.stream().filter(s -> !isBlank(s)).toList();
        if (filledSteps.isEmpty()) {
            errors.add("作り方を1つ以上入力してください");
        } else if (String.join("\n", filledSteps).length() > 4000) {
            errors.add("作り方は合計4000文字以内で入力してください");
        }
    
        if (servings == null || servings < 1 || servings > 20) {
            errors.add("何人分かを1〜20で入力してください");
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

        // テーブルの VARCHAR(1000) に合わせる
        if (memo != null && memo.length() > 1000) {
            errors.add("メモは1000文字以内で入力してください");
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

    public List<String> getSteps() { return steps; }
    public void setSteps(List<String> steps) { this.steps = steps; }

    public List<String> getIngredientNames() { return ingredientNames; }
    public void setIngredientNames(List<String> ingredientNames) { this.ingredientNames = ingredientNames; }

    public List<String> getIngredientQuantities() { return ingredientQuantities; }
    public void setIngredientQuantities(List<String> ingredientQuantities) { this.ingredientQuantities = ingredientQuantities; }

    public List<String> getIngredientUnits() { return ingredientUnits; }
    public void setIngredientUnits(List<String> ingredientUnits) { this.ingredientUnits = ingredientUnits; }


    public Integer getServings() { return servings; }
    public void setServings(Integer servings) { this.servings = servings; }

    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }

}