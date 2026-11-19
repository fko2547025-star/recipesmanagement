package com.example.recipesmanagement.user;

import java.util.regex.Pattern;

/**
 * ユーザー名（ログイン用の ID）の入力ルール（新規登録で使う）。
 *
 * すでに登録しているユーザーには当てない（今のユーザー名のままログインできる）。
 */
public final class UsernameRules {

    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 50;   // DB の列（VARCHAR(50)）に合わせる

    // 半角の英字・数字と「_」「-」だけ
    private static final Pattern PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");

    // static メソッドだけのクラスなので、new できないようにする
    private UsernameRules() {
    }

    /** 前後の空白を取る。null なら空文字にする */
    public static String normalize(String username) {
        return username == null ? "" : username.trim();
    }

    /**
     * 入力チェック（normalize したあとの値を渡す）。
     *
     * @return エラーメッセージ。問題が無ければ null
     */
    public static String check(String username) {
        if (username == null || username.isEmpty()) {
            return "ユーザーIDを入力してください";
        }
        if (username.length() < MIN_LENGTH || username.length() > MAX_LENGTH) {
            return "ユーザーIDは" + MIN_LENGTH + "〜" + MAX_LENGTH + "文字で入力してください";
        }
        if (!PATTERN.matcher(username).matches()) {
            return "ユーザーIDは半角の英字・数字と「_」「-」だけで入力してください";
        }
        return null;
    }
}
