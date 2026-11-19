package com.example.recipesmanagement.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.recipesmanagement.recipe.Recipe;

/**
 * AI の答えを読み取る処理（作り方の下書き・献立の★）のテスト。AI は呼ばない。
 */
class AiAnswerParsingTest {

    @Test
    @SuppressWarnings("unchecked")
    void 下書きから説明と手順を取り出す() {
        String answer = """
                **説明**：甘辛いタレがご飯に合う定番おかず。
                **作り方**：
                1. 豚肉に片栗粉をまぶす。
                2）フライパンで豚肉を焼く。
                3. タレを加えて絡める。
                """;
        Map<String, Object> draft = AiController.parseDraft(answer);
        assertThat(draft.get("description")).isEqualTo("甘辛いタレがご飯に合う定番おかず。");
        assertThat((List<String>) draft.get("steps"))
                .containsExactly("豚肉に片栗粉をまぶす。", "フライパンで豚肉を焼く。", "タレを加えて絡める。");
    }

    @Test
    @SuppressWarnings("unchecked")
    void 形が崩れた答えなら手順は空() {
        Map<String, Object> draft = AiController.parseDraft("すみません、わかりません。");
        assertThat((List<String>) draft.get("steps")).isEmpty();
    }

    @Test
    void 献立の星の付いたレシピを候補にする() {
        LocalDate monday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));   // 来週の月曜日
        Map<String, Recipe> recipes = new LinkedHashMap<>();
        recipes.put("豚の生姜焼き", recipe(1L, "豚の生姜焼き"));
        recipes.put("親子丼", recipe(2L, "親子丼"));

        String answer = """
                月：★親子丼（卵を使う）
                火：鮭の塩焼き（魚）
                水：**★豚の 生姜焼き**（空白と太字）
                木：★存在しない料理（テスト）
                買い足すもの：鮭
                """;
        List<Map<String, String>> picks = AiController.mealPicks(answer, recipes, monday);

        assertThat(picks).extracting(p -> p.get("value")).containsExactly(
                monday + "|夜|2",                // 月曜：親子丼
                monday.plusDays(2) + "|夜|1");   // 水曜：豚の生姜焼き（空白と ** があっても見つかる）
    }

    @Test
    void 過ぎた日は候補にしない() {
        LocalDate lastMonday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1);
        Map<String, Recipe> recipes = Map.of("親子丼", recipe(2L, "親子丼"));
        assertThat(AiController.mealPicks("月：★親子丼（理由）", recipes, lastMonday)).isEmpty();
    }

    private static Recipe recipe(Long id, String title) {
        Recipe r = new Recipe();
        r.setId(id);
        r.setTitle(title);
        r.setCategory("和食");
        return r;
    }
}
