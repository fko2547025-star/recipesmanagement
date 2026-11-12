package com.example.recipesmanagement.mypage;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.recipesmanagement.comment.CommentRepository;
import com.example.recipesmanagement.like.LikeRepository;
import com.example.recipesmanagement.mealplan.MealPlan;
import com.example.recipesmanagement.mealplan.MealPlanRepository;
import com.example.recipesmanagement.recipe.RecipeRepository;
import com.example.recipesmanagement.user.AppUser;
import com.example.recipesmanagement.mealplan.MealPlanService;

/**
 * マイページの「料理レベル」と「スタンプカード」を計算するクラス。
 */
@Service
public class CookingRecordService {

    // ポイントの決め方（1回あたり）
    public static final int POINT_POST = 10;   // レシピを投稿した
    public static final int POINT_LIKE = 5;    // 自分のレシピにいいねをもらった
    public static final int POINT_COOK = 3;    // 献立で「作った」にした

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
     * 料理レベル。投稿数・もらったいいね・作った回数からポイントを計算する。
     */
    @Transactional(readOnly = true)
    public CookingLevel level(AppUser user) {
        long points = recipeRepository.countByUser(user) * POINT_POST
                + likeRepository.countByRecipe_User(user) * POINT_LIKE
                + mealPlanRepository.countByUserAndCookedTrue(user) * POINT_COOK;
        return new CookingLevel((int) points);
    }

    /**
     * 1週間分のスタンプカード。
     *
     * @param anyDay 表示したい週に含まれる日（null なら今週）
     */
    @Transactional(readOnly = true)
    public StampCard stampCard(AppUser user, LocalDate anyDay) {
        LocalDate today = LocalDate.now();
        // 献立と同じく、その日を含む週の月曜日にそろえる
        LocalDate weekStart = MealPlanService.weekStart(anyDay != null ? anyDay : today);
        LocalDate weekEnd = weekStart.plusDays(6);

        // 「作った」日付の集まり。表示する週と、連続日数を数えるための今日までの60日分を両方含める
        LocalDate from = weekStart.isBefore(today.minusDays(60)) ? weekStart : today.minusDays(60);
        LocalDate to = weekEnd.isAfter(today) ? weekEnd : today;
        Set<LocalDate> cookedDates = mealPlanRepository
                .findByUserAndCookedTrueAndPlanDateBetween(user, from, to)
                .stream()
                .map(MealPlan::getPlanDate)
                .collect(Collectors.toSet());

        // ----- 月〜日の7日分 -----
        List<StampCard.Day> days = new ArrayList<>();
        int cookedDays = 0;
        for (int i = 0; i < 7; i++) {
            LocalDate d = weekStart.plusDays(i);
            boolean cooked = cookedDates.contains(d);
            if (cooked) {
                cookedDays++;
            }
            days.add(new StampCard.Day(d, cooked, d.equals(today), d.isAfter(today)));
        }

        // ----- 連続日数（どの週を見ていても、今日を基準に数える） -----
        LocalDate d = cookedDates.contains(today) ? today : today.minusDays(1);
        int streak = 0;
        while (cookedDates.contains(d)) {
            streak++;
            d = d.minusDays(1);
        }

        boolean currentWeek = weekStart.equals(MealPlanService.weekStart(today));
        return new StampCard(weekStart, days, cookedDays, streak, currentWeek);
    }

    /**
     * ごほうびの達成状況（今までのスタンプの合計から計算する）。
     */
    @Transactional(readOnly = true)
    public RewardStatus rewards(AppUser user) {
        return new RewardStatus((int) mealPlanRepository.countCookedDays(user));
    }
}