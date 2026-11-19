package com.example.recipesmanagement.user;

import java.nio.charset.StandardCharsets;

/**
 * パスワードの入力ルール（新規登録・パスワードの変更・パスワードの再設定で共通）。
 *
 * すでに登録しているパスワードには当てない（短いパスワードのままでもログインできる）。
 * 新しく決めるパスワードだけ、このルールで確かめる。
 */
public final class PasswordRules {

    // 最低文字数。短いパスワードは、総当たりで当てられやすい
    public static final int MIN_LENGTH = 8;

    // 最大の長さ（バイト数）。パスワードを暗号化する BCrypt は 72 バイトまでしか扱えず、
    // それより長いとエラーになるため。日本語は1文字が3バイトなので、日本語だけなら24文字まで
    public static final int MAX_BYTES = 72;

    // static メソッドだけのクラスなので、new できないようにする
    private PasswordRules() {
    }

    /**
     * 入力チェック。
     *
     * @return エラーメッセージ。問題が無ければ null
     */
    public static String check(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            return "パスワードは" + MIN_LENGTH + "文字以上で入力してください";
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            return "パスワードが長すぎます（半角で" + MAX_BYTES + "文字まで）";
        }
        return null;
    }
}
