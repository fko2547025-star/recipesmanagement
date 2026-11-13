package com.example.recipesmanagement.mypage;

/**
 * 料理レベル（＝称号）。ポイントを渡すと、レベル・称号・次のレベルまでの残りを計算する。
 * DB とは関係ない。
 */
public class CookingLevel {

    // それぞれのレベルになるのに必要なポイント（0pt でレベル1、30pt でレベル2 …）
    private static final int[] THRESHOLDS = {0, 30, 70, 120, 200, 300, 450, 650};
    private static final String[] TITLES = {
            "見習いコック", "家庭の料理人", "台所の常連", "頼れるシェフ",
            "腕利きの料理人", "台所の達人", "料理マスター", "伝説のシェフ"};
    private static final String[] ICONS = {"🥚", "🍳", "🥘", "🍲", "🔪", "👨‍🍳", "🏅", "👑"};

    // ランクの色の名前（CSS のクラス名 rank--〇〇・frame--〇〇 に使う。AvatarFrame の key と同じ）
    private static final String[] RANKS = {
            "rookie", "bronze", "silver", "gold", "platinum", "diamond", "master", "predator"};

    private final int points;   // 今のポイント
    private final int index;    // 今のレベルの番号（0 から始まる）

    public CookingLevel(int points) {
        this.points = points;
        // 必要ポイントを超えている中で、いちばん高いレベルを探す
        int i = 0;
        for (int k = 0; k < THRESHOLDS.length; k++) {
            if (points >= THRESHOLDS[k]) {
                i = k;
            }
        }
        this.index = i;
    }

    public int getPoints() { return points; }
    public int getLevel() { return index + 1; }          // 画面には 1 から表示する
    public String getTitle() { return TITLES[index]; }   // 称号
    public String getIcon() { return ICONS[index]; }
    /** 今のランクの色の名前（例：gold）。画面で rank--gold のようにクラス名にする */
    public String getRank() { return RANKS[index]; }

    /** 称号を1行で（例：Lv.3 台所の常連）。マークはエンブレム（rank.html）で別に表示する */
    public String getLabel() {
        return "Lv." + getLevel() + " " + getTitle();
    }

    /** いちばん上のレベルか */
    public boolean isMax() { return index == THRESHOLDS.length - 1; }

    /** 次のレベルの称号（いちばん上なら null） */
    public String getNextTitle() { return isMax() ? null : TITLES[index + 1]; }

    /** 次のレベルまで、あと何ポイントか */
    public int getPointsToNext() { return isMax() ? 0 : THRESHOLDS[index + 1] - points; }

    /** 今のレベルの中で、どこまで進んだか（0〜100%。バーの長さに使う） */
    public int getProgressPercent() {
        if (isMax()) {
            return 100;
        }
        int from = THRESHOLDS[index];
        int to = THRESHOLDS[index + 1];
        return (points - from) * 100 / (to - from);
    }
}