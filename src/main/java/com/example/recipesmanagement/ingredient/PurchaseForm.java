package com.example.recipesmanagement.ingredient;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.recipesmanagement.user.AppUser;

/**
 * 「買ったものを在庫に追加」の確認画面の入力値（複数行）。
 * 6つの List の同じ番号が、同じ行（1つの食材）になる。
 */
public class PurchaseForm {

    // 「200g」「1.5個」「少々」などを、数字の部分と単位の部分に分ける正規表現
    private static final Pattern AMOUNT = Pattern.compile("^([0-9]+(?:\\.[0-9]+)?)(.*)$");

    private List<String> names = new ArrayList<>();
    private List<String> quantities = new ArrayList<>();   // 空欄や数字以外もありえるので String
    private List<String> units = new ArrayList<>();
    private List<String> categories = new ArrayList<>();
    private List<String> expiryTypes = new ArrayList<>();
    private List<String> expiryDates = new ArrayList<>();  // "2026-11-20" の形

    /**
     * 買い物リストの1件（材料名と買う量）から、1行分の初期値を足す。
     *
     * @param buyText 買う量（例：200g、1個、少々）
     */
    public void addRow(String name, String buyText) {
        String quantity = "1";
        String unit = "個";
        if (buyText != null) {
            Matcher m = AMOUNT.matcher(buyText.trim());
            if (m.matches()) {
                quantity = m.group(1);                                     // 例：200
                unit = m.group(2).isBlank() ? "個" : m.group(2).trim();    // 例：g
            } else if (!buyText.isBlank()) {
                unit = buyText.trim();                                     // 例：少々 → 数量1・単位「少々」
            }
        }
        String category = IngredientGuess.category(name);

        names.add(name);
        quantities.add(quantity);
        units.add(unit);
        categories.add(category);
        expiryTypes.add(IngredientGuess.expiryType(category));
        expiryDates.add(LocalDate.now().plusDays(IngredientGuess.expiryDays(category)).toString());
    }

    /**
     * 入力チェック。問題があればエラーメッセージのリストを返す（無ければ空のリスト）。
     */
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (names.isEmpty()) {
            errors.add("追加する食材がありません");
        }
        for (int i = 0; i < names.size(); i++) {
            String label = (i + 1) + "行目（" + value(names, i) + "）：";
            if (isBlank(value(names, i))) {
                errors.add((i + 1) + "行目：食材名を入力してください");
            }
            try {
                if (Double.parseDouble(value(quantities, i).trim()) < 0) {
                    errors.add(label + "数量は0以上にしてください");
                }
            } catch (NumberFormatException | NullPointerException e) {
                errors.add(label + "数量は数字で入力してください");
            }
            if (isBlank(value(units, i))) {
                errors.add(label + "単位を入力してください");
            }
            String category = value(categories, i);
            if (category == null || !Ingredient.CATEGORIES.contains(category)) {
                errors.add(label + "カテゴリを選んでください");
            }
            String type = value(expiryTypes, i);
            if (!"賞味期限".equals(type) && !"消費期限".equals(type)) {
                errors.add(label + "期限の種類を選んでください");
            }
            try {
                LocalDate.parse(value(expiryDates, i));
            } catch (DateTimeParseException | NullPointerException e) {
                errors.add(label + "期限日を入力してください");
            }
        }
        return errors;
    }

    /**
     * 入力された行を、食材（Ingredient）のリストにする。validate() で問題が無いことを確かめてから呼ぶ。
     */
    public List<Ingredient> toIngredients(AppUser user) {
        List<Ingredient> list = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            Ingredient ing = new Ingredient();
            ing.setUser(user);   // 持ち主はログイン中のユーザー
            ing.setName(names.get(i).trim());
            ing.setQuantity(Double.valueOf(quantities.get(i).trim()));
            ing.setUnit(units.get(i).trim());
            ing.setCategory(categories.get(i));
            ing.setExpiryType(expiryTypes.get(i));
            ing.setExpiryDate(LocalDate.parse(expiryDates.get(i)));
            list.add(ing);
        }
        return list;
    }

    private static String value(List<String> list, int i) {
        return i < list.size() ? list.get(i) : null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    // ===== getter / setter =====
    public List<String> getNames() { return names; }
    public void setNames(List<String> names) { this.names = names; }
    public List<String> getQuantities() { return quantities; }
    public void setQuantities(List<String> quantities) { this.quantities = quantities; }
    public List<String> getUnits() { return units; }
    public void setUnits(List<String> units) { this.units = units; }
    public List<String> getCategories() { return categories; }
    public void setCategories(List<String> categories) { this.categories = categories; }
    public List<String> getExpiryTypes() { return expiryTypes; }
    public void setExpiryTypes(List<String> expiryTypes) { this.expiryTypes = expiryTypes; }
    public List<String> getExpiryDates() { return expiryDates; }
    public void setExpiryDates(List<String> expiryDates) { this.expiryDates = expiryDates; }
}