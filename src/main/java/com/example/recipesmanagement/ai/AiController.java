package com.example.recipesmanagement.ai;

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

import com.example.recipesmanagement.recipe.Recipe;
import com.example.recipesmanagement.recipe.RecipeRepository;

/**
 * AI（料理サポート）の窓口。画面の JavaScript から呼ばれ、結果を JSON で返す。
 *
 * | URL                    | メソッド | 処理                         | ログイン |
 * |------------------------|----------|------------------------------|----------|
 * | /ai/recipes/{id}/ask   | POST     | レシピについて AI に質問する | 必要     |
 *
 * ログイン必須は SecurityConfig の anyRequest().authenticated() で決まっている（料金がかかるため）。
 */
@RestController
@RequestMapping("/ai")
public class AiController {

    // 質問の最大文字数（長すぎる入力で料金がかさまないように）
    private static final int MAX_QUESTION_LENGTH = 500;

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

    public AiController(AiService aiService, RecipeRepository recipeRepository) {
        this.aiService = aiService;
        this.recipeRepository = recipeRepository;
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
