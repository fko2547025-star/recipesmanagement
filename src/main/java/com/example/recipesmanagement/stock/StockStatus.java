package com.example.recipesmanagement.stock;

/**
 * 在庫チェックの結果の種類。
 *
 * enum（列挙型）：決まった選択肢だけを持つ型。
 * String で "足りる" などと書くと打ち間違いに気づけないが、enum ならコンパイル時にエラーになる。
 */
public enum StockStatus {

    ENOUGH("足りる"),
    SHORT("足りない"),
    UNIT_MISMATCH("在庫あり（単位が違うため量は不明）"),
    NONE("在庫なし");

    // 画面に表示する文字
    private final String label;

    StockStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}