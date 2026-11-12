package com.example.recipesmanagement.mealplan;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.recipesmanagement.recipe.Recipe;
import com.example.recipesmanagement.recipe.RecipeRepository;
import com.example.recipesmanagement.user.AppUser;
import com.example.recipesmanagement.stock.CookingLine;
import com.example.recipesmanagement.stock.CookingService;

/**
 * 献立の処理をまとめたクラス。
 */
@Service
public class MealPlanService {

    private final MealPlanRepository mealPlanRepository;
    private final RecipeRepository recipeRepository;
    private final CookingService cookingService;

    public MealPlanService(MealPlanRepository mealPlanRepository,
                           RecipeRepository recipeRepository,
                           CookingService cookingService) {
        this.mealPlanRepository = mealPlanRepository;
        this.recipeRepository = recipeRepository;
        this.cookingService = cookingService;
    }

    /**
     * その日が含まれる週の月曜日を返す。
     * 例：2026-10-29（木）→ 2026-10-26（月）
     *
     * TemporalAdjusters.previousOrSame(MONDAY)：
     *   「その日が月曜ならその日、そうでなければ直前の月曜」に日付を合わせる
     */
    public static LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /**
     * 1週間分（月〜日）の献立を、1日ずつ「朝・昼・夜の枠」に振り分けて返す。
     *
     * @param monday 週の始まり（月曜日）
     * @return 7日分の DayPlans（月曜から順番）。献立の無い日・枠も空で入っている
     */
    @Transactional(readOnly = true)
    public List<DayPlans> getWeek(AppUser user, LocalDate monday) {
        LocalDate today = LocalDate.now();

        // 先に7日分の入れ物を用意する
        List<DayPlans> week = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = monday.plusDays(i);
            week.add(new DayPlans(date, date.equals(today)));
        }

        // 献立を取り出して（日付 → 登録順）、その日の入れ物に振り分ける
        List<MealPlan> plans = mealPlanRepository.findByUserAndPlanDateBetween(
                user, monday, monday.plusDays(6), Sort.by("planDate", "id"));

        for (MealPlan plan : plans) {
            // ChronoUnit.DAYS.between：2つの日付の差（日数）。月曜なら0、日曜なら6 → リストの番号になる
            int index = (int) ChronoUnit.DAYS.between(monday, plan.getPlanDate());
            week.get(index).add(plan);
        }
        return week;
    }

    /**
     * 献立で選べるレシピの一覧。
     * 「自分が投稿したレシピ」と「いいねしたレシピ」（重複は1つにまとめる）。
     *
     * @param selectedRecipeId すでに選ばれているレシピ（詳細画面の「献立に追加」から来たときなど）。
     *                         一覧に無いレシピでも、先頭に入れて選べるようにする
     */
    @Transactional(readOnly = true)
    public List<Recipe> recipeOptions(AppUser user, Long selectedRecipeId) {
        // id → レシピ の Map にして、同じレシピが2回入らないようにする
        Map<Long, Recipe> options = new LinkedHashMap<>();

        if (selectedRecipeId != null) {
            recipeRepository.findById(selectedRecipeId).ifPresent(r -> options.put(r.getId(), r));
        }
        // putIfAbsent：まだ入っていなければ入れる
        for (Recipe r : recipeRepository.findByUserOrderByIdDesc(user)) {
            options.putIfAbsent(r.getId(), r);
        }
        for (Recipe r : recipeRepository.findLikedBy(user)) {
            options.putIfAbsent(r.getId(), r);
        }
        return new ArrayList<>(options.values());
    }

    /**
     * 自分の献立を1件取得する。他人の献立や存在しない id なら 404。
     * 献立は個人のデータなので、食材管理と同じく「存在しない」扱いにする。
     */
    @Transactional(readOnly = true)
    public MealPlan findOwned(Long id, AppUser user) {
        return mealPlanRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @Transactional
    public MealPlan create(MealPlanForm form, AppUser user) {
        MealPlan plan = new MealPlan();
        plan.setUser(user);   // 持ち主はログイン中のユーザー
        applyForm(plan, form);
        return mealPlanRepository.save(plan);
    }

    @Transactional
    public MealPlan update(Long id, MealPlanForm form, AppUser user) {
        MealPlan plan = findOwned(id, user);
        applyForm(plan, form);
        return plan;   // save() 不要：変更検知で自動的に UPDATE される
    }

    /**
     * @return 削除した献立の日付（削除後にその週へ戻るため）
     */
    @Transactional
    public LocalDate delete(Long id, AppUser user) {
        MealPlan plan = findOwned(id, user);
        LocalDate date = plan.getPlanDate();
        mealPlanRepository.delete(plan);
        return date;
    }
        // ===== 作った =====

    /**
     * 「作った」ときに減らす在庫を計算する（確認画面用。在庫はまだ減らさない）。
     */
    @Transactional(readOnly = true)
    public List<CookingLine> previewCooking(Long id, AppUser user) {
        MealPlan plan = findCookable(id, user);
        return cookingService.plan(plan.getRecipe(), user);
    }

    /**
     * 「作った」にして、在庫を減らす。
     *
     * 在庫を減らす処理と「作った」の記録は、全部成功するか全部取り消しになる（@Transactional）。
     * 途中でエラーになっても、「在庫だけ減って作ったになっていない」状態にはならない。
     *
     * @return 減らした内容（メッセージ表示用）
     */
    @Transactional
    public List<CookingLine> cook(Long id, AppUser user) {
        MealPlan plan = findCookable(id, user);

        List<CookingLine> lines = cookingService.plan(plan.getRecipe(), user);
        for (CookingLine line : lines) {
            for (CookingLine.Deduction d : line.getDeductions()) {
                d.apply();   // 在庫を減らす（変更検知で自動的に UPDATE）
            }
        }

        plan.setCooked(true);   // これも変更検知で自動的に UPDATE
        return lines;
    }

    /**
     * 「作った」にできる献立を取得する。
     * レシピが無い献立や、すでに作った献立は 400（Bad Request）。
     */
    private MealPlan findCookable(Long id, AppUser user) {
        MealPlan plan = findOwned(id, user);
        if (plan.getRecipe() == null || plan.isCooked()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        return plan;
    }

    /**
     * フォームの値を献立に写す（登録・更新で共通）。
     */
    private void applyForm(MealPlan plan, MealPlanForm form) {
        plan.setPlanDate(form.getPlanDate());
        plan.setMealType(form.getMealType());

        // レシピは id で受け取っているので、DB から本物を探してセットする
        Recipe recipe = null;
        if (form.getRecipeId() != null) {
            recipe = recipeRepository.findById(form.getRecipeId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST));
        }
        plan.setRecipe(recipe);

        String memo = form.getMemo();
        plan.setMemo(memo == null || memo.isBlank() ? null : memo.trim());
    }
}