package com.example.recipesmanagement.mypage;

import java.util.List;

/**
 * スタンプを集めるともらえる「ごほうび」1つ分。
 */
public class Reward {

    /** ごほうびの一覧（必要なスタンプの少ない順） */
    public static final List<Reward> ALL = List.of(
            new Reward(3,   "🥄", "はじめの一歩",       null,      "バッジ"),
            new Reward(7,   "🍳", "1週間の料理人",      null,      "バッジ"),
            new Reward(15,  "🥉", "台所の常連",         "bronze",  "プロフィール写真に銅の枠"),
            new Reward(30,  "🥈", "料理の習慣マスター", "silver",  "プロフィール写真に銀の枠"),
            new Reward(50,  "🥇", "スタンプコレクター", "gold",    "プロフィール写真に金の枠"),
            new Reward(100, "👑", "伝説のスタンプ帳",   "rainbow", "プロフィール写真に虹色の枠"));

    private final int required;    // 必要なスタンプの数
    private final String icon;     // 絵文字
    private final String name;     // ごほうびの名前
    private final String frame;    // プロフィール写真の枠の種類（CSS のクラス名に使う）。無ければ null
    private final String bonus;    // 特典の説明

    public Reward(int required, String icon, String name, String frame, String bonus) {
        this.required = required;
        this.icon = icon;
        this.name = name;
        this.frame = frame;
        this.bonus = bonus;
    }

    public int getRequired() { return required; }
    public String getIcon() { return icon; }
    public String getName() { return name; }
    public String getFrame() { return frame; }
    public String getBonus() { return bonus; }
}