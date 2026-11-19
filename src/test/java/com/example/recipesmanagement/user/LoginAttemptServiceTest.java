package com.example.recipesmanagement.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * ログインの失敗回数を数えてロックする仕組み（LoginAttemptService）のテスト。
 */
class LoginAttemptServiceTest {

    private static final String IP = "192.0.2.1";

    @Test
    void 続けて10回失敗するとロックされる() {
        LoginAttemptService service = new LoginAttemptService();
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES - 1; i++) {
            service.recordFailure("alice", IP);
        }
        assertThat(service.isLocked("alice", IP)).isFalse();   // 9回まではロックしない

        service.recordFailure("alice", IP);
        assertThat(service.isLocked("alice", IP)).isTrue();    // 10回目でロック
    }

    @Test
    void 大文字小文字を変えてもロックはすり抜けられない() {
        LoginAttemptService service = new LoginAttemptService();
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES; i++) {
            service.recordFailure(i % 2 == 0 ? "Bob" : "bob", IP);
        }
        assertThat(service.isLocked("BOB", IP)).isTrue();
    }

    @Test
    void ほかのユーザーには影響しない() {
        LoginAttemptService service = new LoginAttemptService();
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES; i++) {
            service.recordFailure("carol", IP);
        }
        assertThat(service.isLocked("dave", IP)).isFalse();
    }

    @Test
    void 別の場所からならロックされない() {
        LoginAttemptService service = new LoginAttemptService();
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES; i++) {
            service.recordFailure("erin", "198.51.100.9");   // 攻撃する人の場所
        }
        assertThat(service.isLocked("erin", "198.51.100.9")).isTrue();
        assertThat(service.isLocked("erin", IP)).isFalse();     // 本人の場所
    }

    @Test
    void 同じ場所から多くのユーザー名で失敗すると場所ごと止める() {
        LoginAttemptService service = new LoginAttemptService();
        for (int i = 0; i < LoginAttemptService.IP_MAX_FAILURES - 1; i++) {
            service.recordFailure("user" + i, IP);
        }
        assertThat(service.isLocked("someone", IP)).isFalse();
        service.recordFailure("user-last", IP);
        assertThat(service.isLocked("someone", IP)).isTrue();
        assertThat(service.isLocked("someone", "203.0.113.5")).isFalse();
    }
}
