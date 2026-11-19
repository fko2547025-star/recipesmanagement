package com.example.recipesmanagement.user;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

/**
 * ログインの失敗回数を数えて、続けて失敗したユーザー名をしばらくロックする（パスワードの総当たり対策）。
 *
 * 同じユーザー名で MAX_FAILURES 回続けて失敗すると、LOCK_TIME の間ログインできない。
 * ログインに成功すると、回数は 0 に戻る。
 *
 * 回数はメモリに覚えるだけ（アプリを再起動すると消える）。データベースは変えない。
 * Spring Security は、ログインの成功・失敗のたびに「イベント」を出すので、@EventListener でそれを受け取って数える。
 */
@Component
public class LoginAttemptService {

    public static final int MAX_FAILURES = 10;
    public static final Duration LOCK_TIME = Duration.ofMinutes(15);

    // 覚えておくユーザー名の上限（でたらめなユーザー名で大量に失敗されても、メモリを使い切らないように）
    private static final int MAX_ENTRIES = 10_000;

    /** 1ユーザー名分の記録：続けて失敗した回数と、ロックが解ける時刻（ロックしていなければ null） */
    private record Attempt(int failures, Instant lockedUntil) {
    }

    // キーはユーザー名（大文字・小文字の違いでロックをすり抜けられないよう、小文字にそろえる）
    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    /** そのユーザー名が今ロック中か */
    public boolean isLocked(String username) {
        Attempt attempt = attempts.get(key(username));
        return attempt != null && attempt.lockedUntil() != null && Instant.now().isBefore(attempt.lockedUntil());
    }

    /** パスワードが違ってログインに失敗したとき（存在しないユーザー名も同じように数える） */
    @EventListener
    public void onFailure(AuthenticationFailureBadCredentialsEvent event) {
        recordFailure(String.valueOf(event.getAuthentication().getName()));
    }

    /** ログインに成功したとき：回数を 0 に戻す */
    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        attempts.remove(key(event.getAuthentication().getName()));
    }

    /** 失敗を1回数える。MAX_FAILURES 回になったらロックする */
    void recordFailure(String username) {
        if (attempts.size() >= MAX_ENTRIES) {
            removeExpired();
        }
        attempts.compute(key(username), (k, old) -> {
            // ロックが解けたあとの失敗は、1回目から数え直す
            int failures = (old == null || isExpired(old)) ? 1 : old.failures() + 1;
            Instant lockedUntil = failures >= MAX_FAILURES ? Instant.now().plus(LOCK_TIME) : null;
            return new Attempt(failures, lockedUntil);
        });
    }

    private void removeExpired() {
        attempts.values().removeIf(a -> a.lockedUntil() == null || isExpired(a));
    }

    private static boolean isExpired(Attempt attempt) {
        return attempt.lockedUntil() != null && !Instant.now().isBefore(attempt.lockedUntil());
    }

    private static String key(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }
}
