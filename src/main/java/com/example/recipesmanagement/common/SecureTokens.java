package com.example.recipesmanagement.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * メールのリンクに付ける「トークン」（推測されにくい文字列）を作る道具。
 * パスワードの再設定と、メールアドレスの確認で使う。
 *
 * データベースには、トークンそのものではなくハッシュ（hash）だけを保存する。
 * データベースの中身を見られても、リンクを作り直せないようにするため。
 */
public final class SecureTokens {

    // SecureRandom：推測されにくい乱数（ふつうの Random は、続きを予想されることがある）
    private static final SecureRandom RANDOM = new SecureRandom();

    private SecureTokens() {
    }

    /**
     * 推測されにくいトークンを作る。
     * 32バイト（256ビット）の乱数を、URL に入れても問題ない文字（A-Z a-z 0-9 - _）だけの文字列にする。
     */
    public static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256 のハッシュ（16進数64文字）。同じ文字列からは、必ず同じハッシュになる。
     */
    public static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 は Java に必ずあるので、ここには来ない
            throw new IllegalStateException(e);
        }
    }
}
