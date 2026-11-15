package com.example.recipesmanagement.mealplan;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.recipesmanagement.ingredient.Ingredient;
import com.example.recipesmanagement.recipe.Recipe;
import com.example.recipesmanagement.recipe.RecipeIngredient;
import com.example.recipesmanagement.recipe.RecipeRepository;
import com.example.recipesmanagement.stock.IngredientNameMatcher;
import com.example.recipesmanagement.stock.StockCheckService;
import com.example.recipesmanagement.user.AppUser;

/**
 * おまかせ献立：空いている枠に、在庫と期限が近い食材を優先してレシピを入れる。
 */
@Service
public class MealPlanSuggestService {

    // 点数の付け方（数字を変えると、選ばれ方が変わる）
    private static final int POINT_EXPIRING = 3;   // 期限が近い在庫を1つ使う
    private static final int POINT_HAVE = 1;       // 在庫にある材料1つ
    private static final int POINT_MISSING = -2;   // 在庫に無い材料1つ
    private static final int POINT_SAME_CATEGORY = -1;   // 前の枠と同じカテゴリ

    private final MealPlanRepository mealPlanRepository;
    private final RecipeRepository recipeRepository;
    private final MealPlanService mealPlanService;
    private final StockCheckService stockCheckService;

    public MealPlanSuggestService(MealPlanRepository mealPlanRepository,
                                  RecipeRepository recipeRepository,
                                  MealPlanService mealPlanService,
                                  StockCheckService stockCheckService) {
        this.mealPlanRepository = mealPlanRepository;
        this.recipeRepository = recipeRepository;
        this.mealPlanService = mealPlanService;
        this.stockCheckService = stockCheckService;
    }

    /**
     * 1週間分の提案を作る（まだ保存しない）。
     *
     * @param monday    週の始まり
     * @param mealTypes 埋める枠（例：["夜"]）
     */
    @Transactional(readOnly = true)
    public List<SuggestedPlan> suggest(AppUser user, LocalDate monday, List<String> mealTypes) {
        LocalDate today = LocalDate.now();
        LocalDate expiringLimit = today.plusDays(StockCheckService.EXPIRING_DAYS);

        // 1. この週にすでに入っている献立から、「埋まっている枠」と「使っているレシピ」を調べる
        Set<String> filledSlots = new HashSet<>();
        Set<Long> usedRecipeIds = new HashSet<>();
        for (MealPlan p : mealPlanRepository.findByUserAndPlanDateBetween(
                user, monday, monday.plusDays(6), Sort.by("planDate", "id"))) {
            filledSlots.add(p.getPlanDate() + "|" + p.getMealType());
            if (p.getRecipe() != null) {
                usedRecipeIds.add(p.getRecipe().getId());
            }
        }

        // 2. 候補のレシピ（自分の投稿＋いいね）。点数が同じときに毎回同じにならないよう、順番を混ぜる
        List<Recipe> candidates = new ArrayList<>(mealPlanService.recipeOptions(user, null));
        Collections.shuffle(candidates);

        // 3. 在庫（期限が近い順）
        List<Ingredient> stock = stockCheckService.loadStock(user);
        Set<String> coveredExpiring = new HashSet<>();   // もう使う予定に入れた「期限が近い在庫」

        List<SuggestedPlan> result = new ArrayList<>();
        String prevCategory = null;

        // 4. 月曜から順に、空いている枠を1つずつ埋める
        for (int i = 0; i < 7; i++) {
            LocalDate date = monday.plusDays(i);
            if (date.isBefore(today)) {
                continue;   // 過ぎた日は埋めない
            }
            for (String mealType : MealPlan.MEAL_TYPES) {   // 朝 → 昼 → 夜 の順
                if (!mealTypes.contains(mealType) || filledSlots.contains(date + "|" + mealType)) {
                    continue;   // 選んでいない枠・もう献立がある枠は飛ばす
                }

                // 全部の候補に点数を付けて、いちばん高いものを選ぶ
                Candidate best = null;
                for (Recipe recipe : candidates) {
                    if (usedRecipeIds.contains(recipe.getId())) {
                        continue;   // 同じ週に同じレシピは入れない
                    }
                    Candidate c = score(recipe, stock, expiringLimit, coveredExpiring, prevCategory);
                    if (best == null || c.score > best.score) {
                        best = c;
                    }
                }
                if (best == null) {
                    return result;   // 使えるレシピが無くなったら、そこまで
                }

                result.add(new SuggestedPlan(date, mealType, best.recipe,
                        List.copyOf(best.expiring), best.haveCount, best.missing));
                usedRecipeIds.add(best.recipe.getId());
                coveredExpiring.addAll(best.expiring);
                prevCategory = best.recipe.getCategory();
            }
        }
        return result;
    }

    /**
     * チェックした提案を保存する。
     *
     * @param picked チェックボックスの値（「日付|朝昼夜|レシピid」）
     * @return 追加した件数
     */
    @Transactional
    public int save(AppUser user, List<String> picked) {
        int count = 0;
        for (String value : picked) {
            // 送られてきた値は書き換えられているかもしれないので、1つずつ確かめる
            String[] parts = value.split("\\|");
            if (parts.length != 3 || !MealPlan.MEAL_TYPES.contains(parts[1])) {
                continue;
            }
            LocalDate date;
            Long recipeId;
            try {
                date = LocalDate.parse(parts[0]);
                recipeId = Long.valueOf(parts[2]);
            } catch (RuntimeException e) {
                continue;   // 日付や数字になっていない値は無視する
            }
            String mealType = parts[1];

            // 提案を見ている間に、別の画面でその枠に献立を入れていたら飛ばす
            if (mealPlanRepository.existsByUserAndPlanDateAndMealType(user, date, mealType)) {
                continue;
            }
            Recipe recipe = recipeRepository.findById(recipeId).orElse(null);
            if (recipe == null) {
                continue;
            }

            MealPlan plan = new MealPlan();
            plan.setUser(user);
            plan.setPlanDate(date);
            plan.setMealType(mealType);
            plan.setRecipe(recipe);
            mealPlanRepository.save(plan);
            count++;
        }
        return count;
    }

    // ===== 内部で使う処理 =====

    /** 点数を付けている途中のレシピ1つ分 */
    private static class Candidate {
        final Recipe recipe;
        final Set<String> expiring = new LinkedHashSet<>();   // 使える「期限が近い在庫」（重複なし・順番どおり）
        final List<String> missing = new ArrayList<>();
        int haveCount;
        int score;

        Candidate(Recipe recipe) {
            this.recipe = recipe;
        }
    }

    /**
     * レシピ1つに点数を付ける。
     */
    private Candidate score(Recipe recipe, List<Ingredient> stock, LocalDate expiringLimit,
                            Set<String> coveredExpiring, String prevCategory) {
        Candidate c = new Candidate(recipe);

        for (RecipeIngredient need : recipe.getIngredients()) {
            // この材料に合う在庫（在庫チェックと同じ照らし合わせ方）
            List<Ingredient> matched = stock.stream()
                    .filter(s -> IngredientNameMatcher.matches(s.getName(), need.getName()))
                    .toList();

            if (matched.isEmpty()) {
                // 「少々」など分量の無いもの（調味料が多い）は、足りなくても減点しない
                if (need.getQuantity() != null) {
                    c.missing.add(need.getName());
                }
                continue;
            }

            c.haveCount++;
            for (Ingredient s : matched) {
                boolean expiringSoon = s.getExpiryDate() != null && !s.getExpiryDate().isAfter(expiringLimit);
                if (expiringSoon && !coveredExpiring.contains(s.getName())) {
                    c.expiring.add(s.getName());
                }
            }
        }

        c.score = c.expiring.size() * POINT_EXPIRING
                + c.haveCount * POINT_HAVE
                + c.missing.size() * POINT_MISSING;
        if (Objects.equals(prevCategory, recipe.getCategory())) {
            c.score += POINT_SAME_CATEGORY;
        }
        return c;
    }
}