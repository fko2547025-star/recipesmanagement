package com.example.recipesmanagement.stock;

import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import com.example.recipesmanagement.ingredient.Ingredient;
import com.example.recipesmanagement.recipe.RecipeIngredient;

/**
 * 在庫チェックの結果1行分（レシピの材料1つと、それに合う在庫）。
 * 画面に渡すためだけのクラスで、DB とは関係ない。
 */
public class StockCheckItem {

    private final String name;              // 材料名（レシピ側）
    private final String neededText;        // 必要な量（例：200g、少々）
    private final String stockText;         // 合った在庫（例：豚ロース 150g）。無ければ「-」
    private final StockStatus status;       // 結果
    private final String shortageText;      // 足りない量（例：50g）。SHORT のときだけ
    private final LocalDate nearestExpiry;  // 合った在庫のうち、一番近い期限
    private final boolean expiringSoon;     // 期限間近か

    private StockCheckItem(String name, String neededText, String stockText, StockStatus status,
                           String shortageText, LocalDate nearestExpiry, boolean expiringSoon) {
        this.name = name;
        this.neededText = neededText;
        this.stockText = stockText;
        this.status = status;
        this.shortageText = shortageText;
        this.nearestExpiry = nearestExpiry;
        this.expiringSoon = expiringSoon;
    }

    /**
     * レシピの材料1つと、名前が合った在庫から、チェック結果を作る。
     *
     * @param need      レシピの材料
     * @param matched   名前が合った在庫（期限が近い順に並んでいること）
     * @param soonLimit この日までに期限が来る在庫を「期限間近」とする
     */
    /**
     * レシピの材料1つと、名前が合った在庫から、チェック結果を作る（レシピ詳細の在庫チェック用）。
     */
    public static StockCheckItem of(RecipeIngredient need, List<Ingredient> matched, LocalDate soonLimit) {
        // 中身は下の of にまかせる
        return of(need.getName(), need.getQuantity(), need.getUnit(), matched, soonLimit);
    }

    /**
     * 材料名・必要な量・単位と、名前が合った在庫から、チェック結果を作る。
     * 買い物リストでは、複数のレシピの量を合計した値で比べるので、こちらを使う。
     *
     * @param quantity 必要な量。少々・適量などで量が無いときは null
     * @param matched  名前が合った在庫（期限が近い順に並んでいること）
     */
    public static StockCheckItem of(String name, Double quantity, String unit,
                                    List<Ingredient> matched, LocalDate soonLimit) {
        String unitText = nullToEmpty(unit);
        String neededText = (quantity != null ? format(quantity) : "") + unitText;
        if (neededText.isEmpty()) {
            neededText = "-";
        }

        // ----- 在庫が無い -----
        if (matched.isEmpty()) {
            return new StockCheckItem(name, neededText, "-", StockStatus.NONE, null, null, false);
        }

        String stockText = matched.stream()
                .map(i -> i.getName() + " " + i.getQuantityText() + i.getUnit())
                .collect(Collectors.joining("、"));

        LocalDate nearest = matched.get(0).getExpiryDate();
        boolean soon = nearest != null && !nearest.isAfter(soonLimit);

        // ----- 量を比べる -----
        StockStatus status;
        String shortage = null;

        if (quantity == null) {
            status = StockStatus.ENOUGH;
        } else {
            String key = IngredientNameMatcher.unitKey(unit);
            List<Ingredient> sameUnit = matched.stream()
                    .filter(i -> IngredientNameMatcher.unitKey(i.getUnit()).equals(key))
                    .toList();

            if (sameUnit.isEmpty()) {
                status = StockStatus.UNIT_MISMATCH;
            } else {
                double total = sameUnit.stream().mapToDouble(Ingredient::getQuantity).sum();
                if (total >= quantity) {
                    status = StockStatus.ENOUGH;
                } else {
                    status = StockStatus.SHORT;
                    shortage = format(quantity - total) + unitText;
                }
            }
        }

        return new StockCheckItem(name, neededText, stockText, status, shortage, nearest, soon);
    }


    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    // 末尾の .0 を消して表示する（50.0 → 50）
    private static String format(double value) {
        return new DecimalFormat("0.###").format(value);
    }

    /**
     * 「足りない」か「在庫なし」か（画面で赤字にするため）。
     */
    public boolean isProblem() {
        return status == StockStatus.SHORT || status == StockStatus.NONE;
    }

    // ===== getter =====

    public String getName() { return name; }
    public String getNeededText() { return neededText; }
    public String getStockText() { return stockText; }
    public StockStatus getStatus() { return status; }
    public String getShortageText() { return shortageText; }
    public LocalDate getNearestExpiry() { return nearestExpiry; }
    public boolean isExpiringSoon() { return expiringSoon; }
}