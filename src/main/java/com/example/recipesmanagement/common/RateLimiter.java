package com.example.recipesmanagement.common;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * 「決まった時間の中で、何回まで」を数える（短い時間に大量に送られるのを防ぐ）。
 *
 * 例：rateLimiter.tryAcquire("register", ip, 5, Duration.ofHours(1))
 *     → 同じ IP アドレスから1時間に5回までは true。6回目からは false（1時間たった分から、また使える）。
 *
 * 回数はメモリに覚えるだけ（アプリを再起動すると消える）。データベースは変えない。
 */
@Component
public class RateLimiter {

    // 覚えておく件数の上限（メモリを使い切らないように）
    private static final int MAX_ENTRIES = 10_000;

    // キーは「種類|相手（IP アドレスやユーザー名）」、値はその相手が使った時刻の一覧
    private final Map<String, Deque<Instant>> history = new ConcurrentHashMap<>();

    /**
     * 1回使ってよいか。よければ、使ったことを記録して true を返す。
     *
     * @param kind   種類（"register" など。種類ごとに別々に数える）
     * @param key    相手（IP アドレスやユーザー名）
     * @param max    window の間に使える回数
     * @param window 数える時間の長さ
     */
    public boolean tryAcquire(String kind, String key, int max, Duration window) {
        Instant now = Instant.now();
        Instant from = now.minus(window);
        if (history.size() >= MAX_ENTRIES) {
            // 古い記録しか無い相手を消す（数える時間は種類ごとに違うので、いちばん長い1日で判断する）
            Instant dayAgo = now.minus(Duration.ofDays(1));
            history.values().removeIf(times -> {
                synchronized (times) {
                    return times.isEmpty() || times.peekLast().isBefore(dayAgo);
                }
            });
        }
        Deque<Instant> times = history.computeIfAbsent(kind + "|" + (key == null ? "" : key), k -> new ArrayDeque<>());
        synchronized (times) {
            // 数える時間より前の記録は捨てる
            while (!times.isEmpty() && !times.peekFirst().isAfter(from)) {
                times.pollFirst();
            }
            if (times.size() >= max) {
                return false;
            }
            times.addLast(now);
            return true;
        }
    }
}
