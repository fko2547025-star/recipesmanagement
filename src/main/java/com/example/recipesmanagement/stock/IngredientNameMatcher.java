package com.example.recipesmanagement.stock;

/**
 * 食材名・単位が「同じもの」かを判定するクラス。
 *
 * 名前のルール：
 * ・空白（半角・全角）を取り除いてから比べる
 * ・どちらかがもう一方を含んでいれば同じとみなす（例：「醤油」と「濃口醤油」）
 *
 * 単位のルール：
 * ・空欄は「個」とみなす（「卵 2」と「卵 6個」を比べられるように）
 * ・大文字・小文字の違いは無視する
 */
public final class IngredientNameMatcher {

    // インスタンスを作らせない（static メソッドだけを使う道具箱のクラス）
    private IngredientNameMatcher() {
    }

    /**
     * 比べやすい形にそろえる（空白を取り除く）。
     * [\\s　]：半角の空白類（\\s）と全角スペース（　）
     */
    public static String normalize(String name) {
        return name == null ? "" : name.replaceAll("[\\s　]", "");
    }

    /**
     * 2つの食材名が同じ食材か。
     */
    public static boolean matches(String a, String b) {
        String x = normalize(a);
        String y = normalize(b);
        if (x.isEmpty() || y.isEmpty()) {
            return false;
        }
        return x.contains(y) || y.contains(x);
    }

    /**
     * 単位を比べやすい形にする。
     * 空欄は「個」とみなし、大文字・小文字の違いは無視する。
     */
    public static String unitKey(String unit) {
        if (unit == null || unit.isBlank()) {
            return "個";
        }
        return unit.trim().toLowerCase();
    }
}