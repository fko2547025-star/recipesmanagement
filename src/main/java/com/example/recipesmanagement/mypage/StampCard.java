package com.example.recipesmanagement.mypage;

import java.time.LocalDate;
import java.util.List;

/**
 * 1週間分の料理スタンプカード（マイページに表示する）。DB とは関係ない。
 */
public class StampCard {

    /** 1週間の目標（この日数作ったら達成） */
    public static final int WEEKLY_GOAL = 5;

    /** 1日分 */
    public static class Day {
        private final LocalDate date;
        private final boolean cooked;    // この日に「作った」献立があるか
        private final boolean today;     // 今日か
        private final boolean future;    // まだ来ていない日か（薄く表示する）

        public Day(LocalDate date, boolean cooked, boolean today, boolean future) {
            this.date = date;
            this.cooked = cooked;
            this.today = today;
            this.future = future;
        }

        public LocalDate getDate() { return date; }
        public boolean isCooked() { return cooked; }
        public boolean isToday() { return today; }
        public boolean isFuture() { return future; }
    }

    private final LocalDate weekStart;    // 月曜日
    private final List<Day> days;         // 月〜日の7日分
    private final int cookedDays;         // この週に作った日数
    private final int streak;             // 今日（または昨日）まで何日連続か
    private final boolean currentWeek;    // 今週か

    public StampCard(LocalDate weekStart, List<Day> days, int cookedDays, int streak, boolean currentWeek) {
        this.weekStart = weekStart;
        this.days = days;
        this.cookedDays = cookedDays;
        this.streak = streak;
        this.currentWeek = currentWeek;
    }

    public LocalDate getWeekStart() { return weekStart; }
    public LocalDate getWeekEnd() { return weekStart.plusDays(6); }
    public LocalDate getPrevWeek() { return weekStart.minusWeeks(1); }
    public LocalDate getNextWeek() { return weekStart.plusWeeks(1); }
    public List<Day> getDays() { return days; }
    public int getCookedDays() { return cookedDays; }
    public int getStreak() { return streak; }
    public boolean isCurrentWeek() { return currentWeek; }

    public int getGoal() { return WEEKLY_GOAL; }
    public boolean isGoalAchieved() { return cookedDays >= WEEKLY_GOAL; }

    /** 目標に対して何%か（バーの長さ。100% を超えない） */
    public int getGoalPercent() { return Math.min(100, cookedDays * 100 / WEEKLY_GOAL); }
}