package com.example.recipesmanagement.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * ユーザー名・パスワードの入力ルール（UsernameRules・PasswordRules）のテスト。
 */
class AccountRulesTest {

    @Test
    void ユーザー名は3から50文字の英数字と記号だけ() {
        assertThat(UsernameRules.check("abc")).isNull();
        assertThat(UsernameRules.check("user_name-01")).isNull();
        assertThat(UsernameRules.check("a".repeat(50))).isNull();

        assertThat(UsernameRules.check("")).isNotNull();
        assertThat(UsernameRules.check("ab")).isNotNull();                 // 短すぎる
        assertThat(UsernameRules.check("a".repeat(51))).isNotNull();       // 長すぎる
        assertThat(UsernameRules.check("はるき")).isNotNull();             // 日本語
        assertThat(UsernameRules.check("a b c")).isNotNull();              // 空白
        assertThat(UsernameRules.check("<script>")).isNotNull();           // 記号
    }

    @Test
    void ユーザー名の前後の空白は取る() {
        assertThat(UsernameRules.normalize("  carol  ")).isEqualTo("carol");
        assertThat(UsernameRules.normalize(null)).isEmpty();
    }

    @Test
    void パスワードは8文字以上72バイトまで() {
        assertThat(PasswordRules.check("abcd1234")).isNull();
        assertThat(PasswordRules.check("a".repeat(72))).isNull();

        assertThat(PasswordRules.check(null)).isNotNull();
        assertThat(PasswordRules.check("abc1234")).isNotNull();            // 7文字
        assertThat(PasswordRules.check("a".repeat(73))).isNotNull();       // 73バイト
        assertThat(PasswordRules.check("あ".repeat(25))).isNotNull();      // 日本語25文字＝75バイト
    }
}
