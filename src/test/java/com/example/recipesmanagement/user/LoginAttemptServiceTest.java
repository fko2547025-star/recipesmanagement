package com.example.recipesmanagement.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * ログインの失敗回数を数えてロックする仕組み（LoginAttemptService）のテスト。
 */
class LoginAttemptServiceTest {

    @Test
    void 続けて10回失敗するとロックされる() {
        LoginAttemptService service = new LoginAttemptService();
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES - 1; i++) {
            service.recordFailure("alice");
        }
        assertThat(service.isLocked("alice")).isFalse();   // 9回まではロックしない

        service.recordFailure("alice");
        assertThat(service.isLocked("alice")).isTrue();    // 10回目でロック
    }

    @Test
    void 大文字小文字を変えてもロックはすり抜けられない() {
        LoginAttemptService service = new LoginAttemptService();
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES; i++) {
            service.recordFailure(i % 2 == 0 ? "Bob" : "bob");
        }
        assertThat(service.isLocked("BOB")).isTrue();
    }

    @Test
    void ほかのユーザーには影響しない() {
        LoginAttemptService service = new LoginAttemptService();
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES; i++) {
            service.recordFailure("carol");
        }
        assertThat(service.isLocked("dave")).isFalse();
    }
}
