package com.example.recipesmanagement.mypage;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.recipesmanagement.like.LikeRepository;
import com.example.recipesmanagement.mealplan.MealPlanRepository;
import com.example.recipesmanagement.mealplan.MealPlanService;
import com.example.recipesmanagement.recipe.RecipeRepository;
import com.example.recipesmanagement.user.AppUser;

/**
 * 料理レベル（称号）と、今週の料理スタンプを計算するクラス。
 */
@Service
public class CookingRecordService {

    // ポイントの決め方（1回あたり）
    public static final int POINT_POST = 10;          // レシピを投稿した
    public static final int POINT_LIKE = 5;           // 自分のレシピにいいねをもらった
    public static final int POINT_COOK = 3;           // 献立で「作った」にした
    public static final int POINT_WEEKLY_GOAL = 5;    // 1週間の目標を達成した（1週ごと）

    private final RecipeRepository recipeRepository;
    private final LikeRepository likeRepository;
    private final MealPlanRepository mealPlanRepository;

    public CookingRecordService(RecipeRepository recipeRepository,
                                LikeRepository likeRepository,
                                MealPlanRepository mealPlanRepository) {
        this.recipeRepository = recipeRepository;
        this.likeRepository = likeRepository;
        this.mealPlanRepository = mealPlanRepository;
    }

    /**
     * 料理レベル。投稿・いいね・作った回数・目標を達成した週の数からポイントを計算する。
     * 自分のマイページだけでなく、ほかの人の称号を表示するときにも使う。
     */
    @Transactional(readOnly = true)
    public CookingLevel level(AppUser user) {
        long points = recipeRepository.countByUser(user) * POINT_POST
                + likeRepository.countByRecipe_User(user) * POINT_LIKE
                + mealPlanRepository.countByUserAndCookedTrue(user) * POINT_COOK
                + countAchievedWeeks(cookedDates(user)) * POINT_WEEKLY_GOAL;
        return new CookingLevel((int) points);
    }

    /**
     * 今週の料理スタンプ（今週の月〜日に、作った日はどこか）。
     */
    @Transactional(readOnly = true)
    public StampCard stampCard(AppUser user) {
        LocalDate today = LocalDate.now();
        LocalDate monday = MealPlanService.weekStart(today);   // 献立と同じ、月曜はじまりの週
        Set<LocalDate> cookedDates = cookedDates(user);

        // ----- 月〜日の7日分 -----
        List<StampCard.Day> days = new ArrayList<>();
        int cookedDays = 0;
        for (int i = 0; i < 7; i++) {
            LocalDate d = monday.plusDays(i);
            boolean cooked = cookedDates.contains(d);
            if (cooked) {
                cookedDays++;
            }
            days.add(new StampCard.Day(d, cooked, d.equals(today), d.isAfter(today)));
        }

        // ----- 連続日数 -----
        // 今日作っていればそこから、まだなら昨日から、さかのぼって続いている日数を数える
        LocalDate d = cookedDates.contains(today) ? today : today.minusDays(1);
        int streak = 0;
        while (cookedDates.contains(d)) {
            streak++;
            d = d.minusDays(1);
        }

        return new StampCard(monday, days, cookedDays, streak, true);
    }

    // ===== 内部で使う処理 =====

    /** 「作った」日付の集まり。Set：contains（入っているか）を速く調べられる、重複しない入れ物 */
    private Set<LocalDate> cookedDates(AppUser user) {
        return new HashSet<>(mealPlanRepository.findCookedDates(user));
    }

    /**
     * 目標（StampCard.WEEKLY_GOAL 日）を達成した週の数。
     * 「作った」日付を、その週の月曜日ごとにまとめて数え、目標日数以上の週を数える。
     */
    private long countAchievedWeeks(Set<LocalDate> cookedDates) {
        return cookedDates.stream()
                // groupingBy：月曜日ごとにまとめて、その週に何日作ったかを数える（SQL の GROUP BY と同じ）
                .collect(Collectors.groupingBy(MealPlanService::weekStart, Collectors.counting()))
                .values().stream()
                .filter(days -> days >= StampCard.WEEKLY_GOAL)
                .count();
    }
    /**
     * その人のランクの色の名前（例：gold）。アイコンの枠に使う。
     * 画面のアイコンの部品（avatar.html）から、${@cookingRecordService.rankOf(user)} で呼ばれる。
     */
    @Transactional(readOnly = true)
    public String rankOf(AppUser user) {
        return user == null ? null : level(user).getRank();
    }
}