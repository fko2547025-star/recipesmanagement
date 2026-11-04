package com.example.recipesmanagement.stock;

import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import com.example.recipesmanagement.ingredient.Ingredient;

/**
 * 「作った」ときに、レシピの材料1つについて在庫をどれだけ減らすか。
 *
 * deductions：減らす在庫のリスト（期限が近い順）。1つの材料で複数の在庫から減らすこともある
 * note：減らせなかった理由や、足りなかった量（問題が無ければ null）
 */
public class CookingLine {

    private final String name;                    // 材料名（レシピ側）
    private final String neededText;              // 必要な量（例：200g）
    private final List<Deduction> deductions;     // 減らす在庫
    private final String note;                    // 備考（在庫なし、単位が違う、あと50g足りない など）

    public CookingLine(String name, String neededText, List<Deduction> deductions, String note) {
        this.name = name;
        this.neededText = neededText;
        this.deductions = deductions;
        this.note = note;
    }

    /**
     * 結果を1行の文章にする（作った後のメッセージ用）。
     * 例：「豚ロース：300g → 100g」「玉ねぎ：在庫なし」
     */
    public String getSummary() {
        String text = deductions.stream()
                .map(d -> d.getBeforeText() + " → " + d.getAfterText())
                .collect(Collectors.joining("、"));
        if (note != null) {
            text = text.isEmpty() ? note : text + "（" + note + "）";
        }
        return name + "：" + text;
    }

    public String getName() { return name; }
    public String getNeededText() { return neededText; }
    public List<Deduction> getDeductions() { return deductions; }
    public String getNote() { return note; }

    /**
     * 1つの在庫から減らす量。
     *
     * static な内側のクラス（ネストクラス）：CookingLine と一緒にしか使わないので、中に書いている。
     * 外からは CookingLine.Deduction という名前で使う。
     */
    public static class Deduction {

        private final Ingredient stock;    // 減らす在庫（食材管理の Entity）
        private final double before;       // 減らす前の量
        private final double amount;       // 減らす量

        public Deduction(Ingredient stock, double before, double amount) {
            this.stock = stock;
            this.before = before;
            this.amount = amount;
        }

        /**
         * 実際に在庫を減らす。
         * stock は @Transactional の中で取得した Entity なので、
         * 値を変えるだけで、コミット時に自動的に UPDATE される（変更検知）。
         */
        public void apply() {
            stock.changeQuantity(stock.getQuantity() - amount);
        }

        public String getStockName() { return stock.getName(); }
        public LocalDate getExpiryDate() { return stock.getExpiryDate(); }
        public String getBeforeText() { return format(before) + stock.getUnit(); }
        public String getAfterText() { return format(before - amount) + stock.getUnit(); }
    }

    // 末尾の .0 を消して表示する
    static String format(double value) {
        return new DecimalFormat("0.###").format(value);
    }
}