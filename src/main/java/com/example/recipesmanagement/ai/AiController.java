package com.example.recipesmanagement.ai;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.recipesmanagement.ingredient.Ingredient;
import com.example.recipesmanagement.recipe.Recipe;
import com.example.recipesmanagement.recipe.RecipeRepository;
import com.example.recipesmanagement.stock.StockCheckService;
import com.example.recipesmanagement.user.AppUser;
import com.example.recipesmanagement.user.AppUserRepository;

/**
 * AI（料理サポート）の窓口。画面の JavaScript から呼ばれ、結果を JSON で返す。
 *
 * | URL                     | メソッド | 処理                           | ログイン |
 * |-------------------------|----------|--------------------------------|----------|
 * | /ai/recipes/{id}/ask    | POST     | レシピについて AI に質問する   | 必要     |
 * | /ai/suggest-from-stock  | POST     | 在庫から作れる料理を提案させる | 必要     |
 *
 * ログイン必須は SecurityConfig の anyRequest().authenticated() で決まっている。
 */
@RestController
@RequestMapping("/ai")
public class AiController {

    // 質問の最大文字数（長すぎる入力で AI の回答が遅くならないように）
    private static final int MAX_QUESTION_LENGTH = 500;

    // 在庫からの提案：希望の最大文字数と、AI に渡す在庫の最大件数（小さいモデルが迷わないように絞る）
    private static final int MAX_REQUEST_LENGTH = 100;
    private static final int MAX_STOCK_ITEMS = 40;

    private static final String STOCK_SYSTEM_PROMPT = """
            あなたは家庭料理のアシスタントです。必ず日本語で答えてください。
            <stock> の中は、利用者の家にある食材です（上にあるものほど期限が近い）。
            これらの食材で作れる家庭料理を2品提案してください。
            ルール：
            1. 期限が近い食材（上のほう）をできるだけ使う。
            2. 調味料（塩・こしょう・しょうゆ・砂糖・油など）は家にあるものとしてよい。
            3. 必ず次の形で、2品ぶん書く。それ以外の前置きやまとめは書かない。

            ■ 料理名
            使う在庫：（使う食材）
            足りない材料：（買い足す食材。なければ「なし」）
            作り方：
            1. …
            2. …
            3. …
            """;

    private static final String RECIPE_SYSTEM_PROMPT = """
            あなたは家庭料理のアシスタントです。必ず日本語で答えてください。
            <recipe> の中は、利用者が見ているレシピです。このレシピを踏まえて質問に答えてください。
            ルール：
            1. 300字以内で、短く具体的に答える。
            2. 手順を説明するときは箇条書きにする。
            3. 生焼け・食中毒・アレルギーなど安全にかかわることは、はっきり伝える。
            4. 料理と関係のない質問には「料理についての質問をどうぞ」とだけ答える。
            """;

    private final AiService aiService;
    private final RecipeRepository recipeRepository;
    private final StockCheckService stockCheckService;
    private final AppUserRepository userRepository;

    public AiController(AiService aiService,
                        RecipeRepository recipeRepository,
                        StockCheckService stockCheckService,
                        AppUserRepository userRepository) {
        this.aiService = aiService;
        this.recipeRepository = recipeRepository;
        this.stockCheckService = stockCheckService;
        this.userRepository = userRepository;
    }

    /**
     * レシピについて AI に質問する。
     *
     * @return 成功：{"answer": "..."}　失敗：{"error": "..."}（400 / 503）
     */
    @PostMapping("/recipes/{id:\\d+}/ask")
    public ResponseEntity<Map<String, String>> askAboutRecipe(@PathVariable Long id,
                                                              @RequestParam(defaultValue = "") String question) {
        String q = question.trim();
        if (q.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "質問を入力してください"));
        }
        if (q.length() > MAX_QUESTION_LENGTH) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "質問は" + MAX_QUESTION_LENGTH + "文字以内で入力してください"));
        }

        Recipe recipe = recipeRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        String message = "<recipe>\n" + describe(recipe) + "</recipe>\n\n質問：" + q;

        try {
            return ResponseEntity.ok(Map.of("answer", aiService.ask(RECIPE_SYSTEM_PROMPT, message)));
        } catch (AiService.AiException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * ログイン中のユーザーの在庫から、作れる料理を AI に提案させる。
     *
     * @param request 希望（任意。例：15分で作れるもの）
     * @return 成功：{"answer": "..."}　失敗：{"error": "..."}（400 / 503）
     */
    @PostMapping("/suggest-from-stock")
    public ResponseEntity<Map<String, String>> suggestFromStock(@RequestParam(defaultValue = "") String request,
                                                                Principal principal) {
        String wish = request.trim();
        if (wish.length() > MAX_REQUEST_LENGTH) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "希望は" + MAX_REQUEST_LENGTH + "文字以内で入力してください"));
        }

        AppUser user = userRepository.findByUsername(principal.getName()).orElseThrow();

        // loadStock：数量が0より多い食材を、期限が近い順に返す。期限切れは AI に渡さない
        List<Ingredient> stock = stockCheckService.loadStock(user).stream()
                .filter(i -> !i.isExpired())
                .limit(MAX_STOCK_ITEMS)
                .toList();

        // 在庫が無いときは、AI を呼ばずに知らせる
        if (stock.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "使える在庫がありません。食材を登録すると提案できます。"));
        }

        String stockText = stock.stream()
                .map(this::describe)
                .collect(Collectors.joining("\n"));

        String message = "<stock>\n" + stockText + "\n</stock>\n\n"
                + (wish.isEmpty() ? "おすすめの料理を2品提案してください。" : "希望：" + wish);

        try {
            return ResponseEntity.ok(Map.of("answer", aiService.ask(STOCK_SYSTEM_PROMPT, message)));
        } catch (AiService.AiException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 在庫1件を、AI に渡す1行にする（例：- 豚こま 200g（期限まであと1日））。
     */
    private String describe(Ingredient item) {
        String line = "- " + item.getName() + " " + item.getQuantityText()
                + (item.getUnit() != null ? item.getUnit() : "");
        if (item.getExpiryDate() == null) {
            return line;
        }
        long days = item.getDaysLeft();
        return line + (days == 0 ? "（期限は今日）" : "（期限まであと" + days + "日）");
    }

    /**
     * レシピの内容を、AI に渡す文章にする。
     */
    private String describe(Recipe recipe) {
        String ingredients = recipe.getIngredients().stream()
                .map(i -> "- " + i.getName() + " " + i.getQuantityText()
                        + (i.getUnit() != null ? i.getUnit() : ""))
                .collect(Collectors.joining("\n"));

        StringBuilder sb = new StringBuilder();
        sb.append("料理名：").append(recipe.getTitle()).append("\n");
        sb.append("カテゴリ：").append(recipe.getCategory()).append("\n");
        sb.append("人数：").append(recipe.getServings()).append("人分\n");
        if (recipe.getDescription() != null && !recipe.getDescription().isBlank()) {
            sb.append("説明：").append(recipe.getDescription()).append("\n");
        }
        sb.append("材料：\n").append(ingredients).append("\n");
        sb.append("作り方：\n");
        int n = 1;
        for (String step : recipe.getStepList()) {
            sb.append(n++).append(". ").append(step).append("\n");
        }
        if (recipe.getMemo() != null && !recipe.getMemo().isBlank()) {
            sb.append("コツ・ポイント：").append(recipe.getMemo()).append("\n");
        }
        return sb.toString();
    }
}
