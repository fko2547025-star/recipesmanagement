package com.example.recipesmanagement.user;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

/**
 * ログインの失敗回数を数えて、しばらくロックする（パスワードの総当たり対策）。
 *
 * 2つの数え方をする：
 *   ① 「ユーザー名＋IP アドレス」ごと：MAX_FAILURES 回続けて失敗すると、その場所からそのユーザー名でログインできない。
 *      他人が別の場所でわざと失敗しても、本人（別の IP アドレス）はロックされない。
 *   ② IP アドレスごと：同じ場所から IP_MAX_FAILURES 回失敗すると（ユーザー名を変えても）、その場所からログインできない。
 *      いろいろなユーザー名を次々に試す攻撃を止める。
 * どちらも LOCK_TIME の間ロックする。ログインに成功すると、①の回数は 0 に戻る。
 *
 * 回数はメモリに覚えるだけ（アプリを再起動すると消える）。データベースは変えない。
 * Spring Security は、ログインの成功・失敗のたびに「イベント」を出すので、@EventListener でそれを受け取って数える。
 */
@Component
public class LoginAttemptService {

    public static final int MAX_FAILURES = 10;
    public static final int IP_MAX_FAILURES = 50;
    public static final Duration LOCK_TIME = Duration.ofMinutes(15);

    // 覚えておく件数の上限（でたらめなユーザー名で大量に失敗されても、メモリを使い切らないように）
    private static final int MAX_ENTRIES = 10_000;

    /**
     * 1件分の記録：失敗した回数、数え始めた時刻、ロックが解ける時刻（ロックしていなければ null）。
     * 数え始めてから LOCK_TIME 過ぎたら、回数は数え直す。
     */
    private record Attempt(int failures, Instant since, Instant lockedUntil) {
    }

    // ① キーは「ユーザー名（小文字）|IP アドレス」
    private final Map<String, Attempt> userAttempts = new ConcurrentHashMap<>();
    // ② キーは IP アドレス
    private final Map<String, Attempt> ipAttempts = new ConcurrentHashMap<>();

    /** その場所（IP アドレス）から、そのユーザー名でのログインがロック中か */
    public boolean isLocked(String username, String ip) {
        return isLocked(userAttempts.get(userKey(username, ip))) || isLocked(ipAttempts.get(ipKey(ip)));
    }

    /** パスワードが違ってログインに失敗したとき（存在しないユーザー名も同じように数える） */
    @EventListener
    public void onFailure(AuthenticationFailureBadCredentialsEvent event) {
        recordFailure(String.valueOf(event.getAuthentication().getName()), ipOf(event.getAuthentication()));
    }

    /** ログインに成功したとき：その場所・そのユーザー名の回数を 0 に戻す */
    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        userAttempts.remove(userKey(event.getAuthentication().getName(), ipOf(event.getAuthentication())));
    }

    /** 失敗を1回数える */
    void recordFailure(String username, String ip) {
        if (userAttempts.size() >= MAX_ENTRIES) {
            removeOld(userAttempts);
        }
        if (ipAttempts.size() >= MAX_ENTRIES) {
            removeOld(ipAttempts);
        }
        count(userAttempts, userKey(username, ip), MAX_FAILURES);
        count(ipAttempts, ipKey(ip), IP_MAX_FAILURES);
    }

    private static void count(Map<String, Attempt> attempts, String key, int max) {
        Instant now = Instant.now();
        attempts.compute(key, (k, old) -> {
            // 初めて・ロックが解けたあと・数え始めてから時間がたったときは、1回目から数え直す
            boolean restart = old == null
                    || (old.lockedUntil() != null && !now.isBefore(old.lockedUntil()))
                    || (old.lockedUntil() == null && now.isAfter(old.since().plus(LOCK_TIME)));
            int failures = restart ? 1 : old.failures() + 1;
            Instant since = restart ? now : old.since();
            Instant lockedUntil = failures >= max ? now.plus(LOCK_TIME) : null;
            return new Attempt(failures, since, lockedUntil);
        });
    }

    private static boolean isLocked(Attempt attempt) {
        return attempt != null && attempt.lockedUntil() != null && Instant.now().isBefore(attempt.lockedUntil());
    }

    /** ロック中でなく、数え始めてから時間がたった記録を消す */
    private static void removeOld(Map<String, Attempt> attempts) {
        Instant now = Instant.now();
        attempts.values().removeIf(a -> !isLocked(a) && now.isAfter(a.since().plus(LOCK_TIME)));
    }

    /** ログインの申し込みの中から、送ってきた場所（IP アドレス）を取り出す */
    private static String ipOf(Authentication authentication) {
        return authentication.getDetails() instanceof WebAuthenticationDetails details
                ? details.getRemoteAddress() : null;
    }

    private static String userKey(String username, String ip) {
        // 大文字・小文字の違いでロックをすり抜けられないよう、小文字にそろえる
        return (username == null ? "" : username.trim().toLowerCase()) + "|" + ipKey(ip);
    }

    private static String ipKey(String ip) {
        return ip == null ? "" : ip;
    }
}
