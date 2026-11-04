package com.example.recipesmanagement.ranking;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.recipesmanagement.like.LikeRepository;
import com.example.recipesmanagement.like.RecipeLikeCount;
import com.example.recipesmanagement.recipe.Recipe;
import com.example.recipesmanagement.recipe.RecipeRepository;

/**
 * いいね数ランキングを作るクラス。
 */
@Service
public class RankingService {

    // 表示する件数
    private static final int TOP = 10;

    private final LikeRepository likeRepository;
    private final RecipeRepository recipeRepository;

    public RankingService(LikeRepository likeRepository, RecipeRepository recipeRepository) {
        this.likeRepository = likeRepository;
        this.recipeRepository = recipeRepository;
    }

    /**
     * ランキングを作る。
     *
     * @param period "week"（直近7日）、"month"（直近30日）、それ以外は累計
     */
    @Transactional(readOnly = true)
    public List<RankingItem> getRanking(String period) {

        // ----- 1. 期間の開始日時を決める -----
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime since = switch (period) {
            case "week"  -> now.minusDays(7);
            case "month" -> now.minusDays(30);
            default      -> LocalDateTime.of(1970, 1, 1, 0, 0);   // 累計：十分古い日時から
        };

        // ----- 2. いいねを集計（上位 TOP 件の「レシピの id・いいね数」） -----
        // PageRequest.of(0, TOP)：0ページ目（最初）から TOP 件
        List<RecipeLikeCount> counts = likeRepository.countLikesSince(since, PageRequest.of(0, TOP));

        // ----- 3. そのレシピをまとめて取得し、id で引けるように Map にする -----
        // stream()：リストの中身を1つずつ流して加工する書き方
        //   map(...)：RecipeLikeCount → レシピの id に変換
        //   toList()：結果をリストにまとめる
        List<Long> ids = counts.stream()
                .map(RecipeLikeCount::getRecipeId)
                .toList();

        // Collectors.toMap：リストを「id → レシピ」の Map に変換
        Map<Long, Recipe> recipes = recipeRepository.findByIdIn(ids).stream()
                .collect(Collectors.toMap(Recipe::getId, Function.identity()));

        // ----- 4. 順位を付ける（同じいいね数なら同じ順位：1, 2, 2, 4 …） -----
        List<RankingItem> result = new ArrayList<>();
        int rank = 0;
        long previousCount = -1;

        for (int i = 0; i < counts.size(); i++) {
            long count = counts.get(i).getLikeCount();

            // 前の行といいね数が違えば、順位を「何番目か」にする。同じなら前の順位のまま
            if (count != previousCount) {
                rank = i + 1;
                previousCount = count;
            }

            Recipe recipe = recipes.get(counts.get(i).getRecipeId());
            result.add(new RankingItem(rank, recipe, count));
        }
        return result;
    }
}