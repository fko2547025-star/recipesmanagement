package com.example.recipesmanagement.ingredient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 材料名から、食材のカテゴリと期限を推測する（買い物リスト → 在庫に追加 の初期値用）。
 * あくまで「最初に入れておく値」なので、外れていても確認画面で直せる。
 */
public class IngredientGuess {

    // カテゴリ → そのカテゴリだと判断する言葉（材料名にこの言葉が含まれていたら、そのカテゴリ）
    // LinkedHashMap：上から順に調べたいので、入れた順番を覚えている Map を使う
    private static final Map<String, List<String>> KEYWORDS = new LinkedHashMap<>();
    static {
        // 「豆腐」を「野菜」より先に調べる、のように、まぎらわしいものを先に並べる
        KEYWORDS.put("豆腐・大豆製品", List.of("豆腐", "油揚げ", "厚揚げ", "納豆", "豆乳"));
        KEYWORDS.put("卵・乳製品", List.of("卵", "牛乳", "バター", "チーズ", "ヨーグルト", "生クリーム"));
        KEYWORDS.put("肉", List.of("豚", "牛", "鶏", "ひき肉", "手羽", "ベーコン", "ハム", "ウインナー", "ソーセージ"));
        KEYWORDS.put("魚介", List.of("鮭", "さば", "ぶり", "あじ", "いわし", "たら", "まぐろ", "さんま", "えび", "いか", "たこ", "あさり", "かにかま"));
        KEYWORDS.put("きのこ", List.of("しめじ", "しいたけ", "えのき", "まいたけ", "エリンギ", "なめこ"));
        KEYWORDS.put("米・パン・麺", List.of("米", "ご飯", "パン", "麺", "うどん", "そば", "パスタ", "スパゲッティ", "マカロニ", "春雨"));
        KEYWORDS.put("調味料", List.of("醤油", "みりん", "砂糖", "塩", "こしょう", "味噌", "酢", "酒", "ソース", "ケチャップ",
                "マヨネーズ", "油", "めんつゆ", "コンソメ", "だし", "豆板醤", "甜麺醤", "片栗粉", "小麦粉", "ルウ", "ドレッシング"));
        KEYWORDS.put("果物", List.of("りんご", "バナナ", "みかん", "いちご", "レモン", "キウイ"));
        KEYWORDS.put("野菜", List.of("玉ねぎ", "にんじん", "じゃがいも", "キャベツ", "大根", "白菜", "ねぎ", "ほうれん草", "小松菜",
                "ピーマン", "なす", "トマト", "きゅうり", "もやし", "ごぼう", "れんこん", "かぼちゃ", "ブロッコリー",
                "生姜", "にんにく", "にら", "水菜", "いんげん", "大葉"));
    }

    // カテゴリ → 期限までのおおよその日数
    private static final Map<String, Integer> EXPIRY_DAYS = Map.ofEntries(
            Map.entry("肉", 2), Map.entry("魚介", 2), Map.entry("豆腐・大豆製品", 4),
            Map.entry("きのこ", 5), Map.entry("米・パン・麺", 5), Map.entry("野菜", 7),
            Map.entry("卵・乳製品", 10), Map.entry("果物", 7), Map.entry("飲み物", 30),
            Map.entry("冷凍食品", 60), Map.entry("調味料", 180), Map.entry("その他", 14));

    /** 材料名から、カテゴリを推測する。どれにも当てはまらなければ「その他」 */
    public static String category(String name) {
        if (name == null) {
            return "その他";
        }
        for (Map.Entry<String, List<String>> e : KEYWORDS.entrySet()) {
            for (String word : e.getValue()) {
                if (name.contains(word)) {
                    return e.getKey();
                }
            }
        }
        return "その他";
    }

    /** カテゴリから、期限までのおおよその日数 */
    public static int expiryDays(String category) {
        return EXPIRY_DAYS.getOrDefault(category, 14);
    }

    /** カテゴリから、期限の種類（傷みやすいものは消費期限） */
    public static String expiryType(String category) {
        return List.of("肉", "魚介", "豆腐・大豆製品").contains(category) ? "消費期限" : "賞味期限";
    }
}