package com.example.recipesmanagement.user;

import java.util.regex.Pattern;

/**
 * メールアドレスの入力ルール（新規登録とプロフィール編集で共通）。
 */
public final class EmailRules {

    public static final int MAX_LENGTH = 255;   // DB の列（VARCHAR(255)）に合わせる

    /*
     * 「空白と @ 以外の文字」 @ 「空白と @ 以外」 . 「空白と @ 以外」 という、ゆるいチェック。
     * メールアドレスの正式なルールは複雑で、完璧に正規表現で確かめるのは難しい。
     * 本当に届くかどうかは、実際にメールを送ってみないとわからないので、ここでは打ち間違いを防ぐ程度にする
     */
    private static final Pattern PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    // static メソッドだけのクラスなので、new できないようにする
    private EmailRules() {
    }

    /** 前後の空白を取る。空なら null にする */
    public static String normalize(String email) {
        if (email == null) {
            return null;
        }
        String trimmed = email.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 入力チェック（normalize したあとの値を渡す）。
     *
     * @return エラーメッセージ。問題が無ければ null
     */
    public static String check(String email) {
        if (email == null) {
            return "メールアドレスを入力してください";
        }
        if (email.length() > MAX_LENGTH) {
            return "メールアドレスは" + MAX_LENGTH + "文字以内で入力してください";
        }
        if (!PATTERN.matcher(email).matches()) {
            return "メールアドレスの形式が正しくありません";
        }
        return null;
    }
}