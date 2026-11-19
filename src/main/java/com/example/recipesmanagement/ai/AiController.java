package com.example.recipesmanagement.ai;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import tools.jackson.databind.json.JsonMapper;

import com.example.recipesmanagement.ingredient.Ingredient;
import com.example.recipesmanagement.mealplan.MealPlanService;
import com.example.recipesmanagement.recipe.Recipe;
import com.example.recipesmanagement.recipe.RecipeRepository;
import com.example.recipesmanagement.stock.StockCheckService;
import com.example.recipesmanagement.user.AppUser;
import com.example.recipesmanagement.user.AppUserRepository;

/**
 * AI（料理サポート）の窓口。画面の JavaScript から呼ばれ、結果を JSON で返す。
 * 「質問」と「在庫から提案」は、回答を少しずつ返す（1行に1つの JSON。{"text": "続き"} か {"error": "..."}）。
 *
 * | URL                     | メソッド | 処理                           | ログイン |
 * |-------------------------|----------|--------------------------------|----------|
 * | /ai/recipes/{id}/ask    | POST     | レシピについて AI に質問する   | 必要     |
 * | /ai/suggest-from-stock  | POST     | 在庫から作れる料理を提案させる | 必要     |
 * | /ai/draft-recipe        | POST     | 投稿フォームの作り方を下書き   | 必要     |
 * | /ai/meal-plan           | POST     | 1週間の献立を提案させる        | 必要     |
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

    // 続けて質問するとき、AI に渡すこれまでの会話の数（質問と回答の組）と、1つの回答の最大文字数
    private static final int MAX_HISTORY = 3;
    private static final int MAX_HISTORY_ANSWER_LENGTH = 2000;

    // 少しずつ返す回答の形（1行に1つの JSON）と、少しずつ返すときの待ち時間の上限（3分）
    private static final MediaType NDJSON = MediaType.parseMediaType("application/x-ndjson;charset=UTF-8");
    private static final long STREAM_TIMEOUT_MS = 180_000L;

    private static final String RECIPE_SYSTEM_PROMPT = """
            あなたは家庭料理のアシスタントです。必ず日本語で答えてください。
            <recipe> の中は、利用者が見ているレシピです。このレシピを踏まえて質問に答えてください。
            前の質問の続きを聞かれたら、それまでの会話も踏まえて答えてください。
            ルール：
            1. 300字以内で、短く具体的に答える。
            2. 手順を説明するときは箇条書きにする。
            3. 生焼け・食中毒・アレルギーなど安全にかかわることは、はっきり伝える。
            4. 料理と関係のない質問には「料理についての質問をどうぞ」とだけ答える。
            """;

    // 作り方の下書き：AI に渡す材料の最大件数
    private static final int MAX_DRAFT_INGREDIENTS = 30;

    private static final String DRAFT_SYSTEM_PROMPT = """
            あなたは家庭料理のレシピを書くアシスタントです。必ず日本語で答えてください。
            <draft> の中の料理名・人数・材料をもとに、レシピの「説明」と「作り方」を書いてください。
            ルール：
            1. 説明は1文（50字以内）で、どんな料理かを書く。
            2. 作り方は3〜8手順。1手順は1文で短く書く。
            3. <draft> にある材料を使う。調味料以外の材料を勝手に増やさない。
            4. 必ず次の形だけで答える。前置きやまとめは書かない。

            説明：（ここに説明）
            作り方：
            1. …
            2. …
            3. …
            """;

    // 献立の提案：AI に渡すレシピの最大件数
    private static final int MAX_MEAL_RECIPES = 50;

    private static final String MEAL_PLAN_SYSTEM_PROMPT = """
            あなたは家庭の献立を考えるアシスタントです。必ず日本語で答えてください。
            <recipes> は利用者が登録しているレシピ、<stock> は家にある食材です（上ほど期限が近い）。
            月曜から日曜までの7日分の夕食を考えてください。
            ルール：
            1. できるだけ <recipes> の中のレシピを使い、その場合は料理名の前に「★」を付ける。
            2. 期限が近い食材を、早い曜日で使う。
            3. 同じ料理を続けない。肉・魚・野菜のバランスをとる。
            4. 必ず次の形で7行だけ書き、最後に「買い足すもの：」を1行書く。前置きは書かない。

            月：料理名（ひとこと理由）
            火：料理名（ひとこと理由）
            水：料理名（ひとこと理由）
            木：料理名（ひとこと理由）
            金：料理名（ひとこと理由）
            土：料理名（ひとこと理由）
            日：料理名（ひとこと理由）
            買い足すもの：（なければ「なし」）
            """;

    private final AiService aiService;
    private final RecipeRepository recipeRepository;
    private final StockCheckService stockCheckService;
    private final AppUserRepository userRepository;
    private final MealPlanService mealPlanService;
    private final AsyncTaskExecutor taskExecutor;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    /**
     * @param taskExecutor Spring Boot が用意する「裏で処理を動かす」仕組み。AI の回答を待ちながら画面に送るのに使う
     */
    public AiController(AiService aiService,
                        RecipeRepository recipeRepository,
                        StockCheckService stockCheckService,
                        AppUserRepository userRepository,
                        MealPlanService mealPlanService,
                        @Qualifier("applicationTaskExecutor") AsyncTaskExecutor taskExecutor) {
        this.aiService = aiService;
        this.recipeRepository = recipeRepository;
        this.stockCheckService = stockCheckService;
        this.userRepository = userRepository;
        this.mealPlanService = mealPlanService;
        this.taskExecutor = taskExecutor;
    }

    /**
     * レシピについて AI に質問する。
     *
     * @param servings         画面で表示している人数。分量をこの人数分に換算して AI に渡す（無い・範囲外ならレシピの人数）
     * @param historyQuestions これまでの質問（古い順）。続けて質問するときに画面から送られてくる
     * @param historyAnswers   これまでの回答（historyQuestions と同じ順番）
     * @return 回答を少しずつ（NDJSON）。入力の誤りは {"error": "..."} の1行（400）
     */
    @PostMapping("/recipes/{id:\\d+}/ask")
    public ResponseEntity<ResponseBodyEmitter> askAboutRecipe(@PathVariable Long id,
                                            @RequestParam(defaultValue = "") String question,
                                            @RequestParam(required = false) Integer servings,
                                            @RequestParam(required = false) List<String> historyQuestions,
                                            @RequestParam(required = false) List<String> historyAnswers,
                                            Principal principal) {
        String q = question.trim();
        if (q.isEmpty()) {
            return streamError("質問を入力してください");
        }
        if (q.length() > MAX_QUESTION_LENGTH) {
            return streamError("質問は" + MAX_QUESTION_LENGTH + "文字以内で入力してください");
        }

        Recipe recipe = recipeRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        // レシピ詳細と同じく、1〜20人分だけ受け付ける
        int people = (servings != null && servings >= 1 && servings <= 20) ? servings : recipe.getServings();

        // レシピの内容は AI への指示（system）に入れる。会話が続いても毎回同じレシピを踏まえて答えられる
        String system = RECIPE_SYSTEM_PROMPT
                + "\n<recipe>\n" + describe(recipe, people) + "</recipe>"
                + avoidNote(currentUser(principal),
                        "このレシピに含まれる場合は、そのことと代わりの食材を必ず伝えてください。");

        List<Map<String, String>> messages = history(historyQuestions, historyAnswers);
        messages.add(AiService.userMessage(q));

        return streamAnswer(system, messages);
    }

    /**
     * ログイン中のユーザーの在庫から、作れる料理を AI に提案させる。
     *
     * @param request 希望（任意。例：15分で作れるもの）
     * @return 回答を少しずつ（NDJSON）。入力の誤り・在庫なしは {"error": "..."} の1行（400）
     */
    @PostMapping("/suggest-from-stock")
    public ResponseEntity<ResponseBodyEmitter> suggestFromStock(@RequestParam(defaultValue = "") String request,
                                                                Principal principal) {
        String wish = request.trim();
        if (wish.length() > MAX_REQUEST_LENGTH) {
            return streamError("希望は" + MAX_REQUEST_LENGTH + "文字以内で入力してください");
        }

        AppUser user = currentUser(principal);
        List<Ingredient> stock = usableStock(user);

        // 在庫が無いときは、AI を呼ばずに知らせる
        if (stock.isEmpty()) {
            return streamError("使える在庫がありません。食材を登録すると提案できます。");
        }

        String message = "<stock>\n" + describeStock(stock) + "\n</stock>\n\n"
                + (wish.isEmpty() ? "おすすめの料理を2品提案してください。" : "希望：" + wish)
                + avoidNote(user, "これらを使う料理は提案しないでください。");

        return streamAnswer(STOCK_SYSTEM_PROMPT, List.of(AiService.userMessage(message)));
    }

    /**
     * 投稿・編集フォームの内容から、説明と作り方の下書きを AI に書かせる。
     * フォームの JavaScript から呼ばれ、結果をフォームに入れる（まだ保存はしない）。
     *
     * @return 成功：{"description": "...", "steps": ["...", ...]}　失敗：{"error": "..."}（400 / 503）
     */
    @PostMapping("/draft-recipe")
    public ResponseEntity<Map<String, Object>> draftRecipe(
            @RequestParam(defaultValue = "") String title,
            @RequestParam(defaultValue = "2") int servings,
            @RequestParam(required = false) List<String> ingredientNames,
            @RequestParam(required = false) List<String> ingredientQuantities,
            @RequestParam(required = false) List<String> ingredientUnits,
            @RequestParam(defaultValue = "") String memo,
            Principal principal) {

        String name = title.trim();
        if (name.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "先に料理名を入力してください"));
        }
        if (name.length() > 100 || memo.length() > 1000) {
            return ResponseEntity.badRequest().body(Map.of("error", "入力が長すぎます"));
        }

        // 材料：名前が空の行は飛ばし、「豚ロース 200g」の形にする
        List<String> lines = new ArrayList<>();
        if (ingredientNames != null) {
            for (int i = 0; i < ingredientNames.size() && lines.size() < MAX_DRAFT_INGREDIENTS; i++) {
                String n = ingredientNames.get(i).trim();
                if (n.isEmpty() || n.length() > 100) {
                    continue;
                }
                lines.add("- " + n + " " + valueAt(ingredientQuantities, i) + valueAt(ingredientUnits, i));
            }
        }

        StringBuilder sb = new StringBuilder("<draft>\n");
        sb.append("料理名：").append(name).append("\n");
        sb.append("人数：").append(Math.max(1, Math.min(servings, 20))).append("人分\n");
        sb.append("材料：\n").append(lines.isEmpty() ? "（未入力。料理名から考えてよい）" : String.join("\n", lines)).append("\n");
        if (!memo.isBlank()) {
            sb.append("メモ：").append(memo.trim()).append("\n");
        }
        sb.append("</draft>");
        sb.append(avoidNote(currentUser(principal), "<draft> にない材料や調味料を足すときは、これらを使わないでください。"));

        String text;
        try {
            text = aiService.ask(DRAFT_SYSTEM_PROMPT, sb.toString());
        } catch (AiService.AiException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", e.getMessage()));
        }

        Map<String, Object> draft = parseDraft(text);
        if (((List<?>) draft.get("steps")).isEmpty()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "AIの下書きをうまく読み取れませんでした。もう一度お試しください。"));
        }
        return ResponseEntity.ok(draft);
    }

    /**
     * 選べるレシピ（自分の投稿・いいねしたレシピ）と在庫から、1週間の夕食を AI に提案させる。
     * 提案は表示するだけで、献立には保存しない。
     *
     * @param request 希望（任意。例：野菜多め）
     * @return 成功：{"answer": "..."}　失敗：{"error": "..."}（400 / 503）
     */
    @PostMapping("/meal-plan")
    public ResponseEntity<Map<String, String>> suggestMealPlan(@RequestParam(defaultValue = "") String request,
                                                               Principal principal) {
        String wish = request.trim();
        if (wish.length() > MAX_REQUEST_LENGTH) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "希望は" + MAX_REQUEST_LENGTH + "文字以内で入力してください"));
        }

        AppUser user = currentUser(principal);

        // 献立の登録画面と同じ「選べるレシピ」。料理名が同じものは1つにまとめる
        Map<String, String> recipes = new LinkedHashMap<>();
        for (Recipe r : mealPlanService.recipeOptions(user, null)) {
            if (recipes.size() >= MAX_MEAL_RECIPES) {
                break;
            }
            recipes.putIfAbsent(r.getTitle(), "- " + r.getTitle() + "（" + r.getCategory() + "）");
        }
        List<Ingredient> stock = usableStock(user);

        String message = "<recipes>\n"
                + (recipes.isEmpty() ? "（登録なし）" : String.join("\n", recipes.values()))
                + "\n</recipes>\n\n<stock>\n"
                + (stock.isEmpty() ? "（在庫なし）" : describeStock(stock))
                + "\n</stock>\n\n"
                + (wish.isEmpty() ? "1週間の夕食を考えてください。" : "希望：" + wish)
                + avoidNote(user, "これらを使う料理は献立に入れないでください。");

        try {
            return ResponseEntity.ok(Map.of("answer", aiService.ask(MEAL_PLAN_SYSTEM_PROMPT, message)));
        } catch (AiService.AiException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", e.getMessage()));
        }
    }

    // ===== 内部で使う処理 =====

    /**
     * AI の回答を、届いた分から画面に送る。
     * 1行に1つの JSON：回答の続きは {"text": "..."}、途中で失敗したら {"error": "..."}。
     * 回答を送り始めた後は HTTP の状態（200 など）を変えられないので、エラーも同じ形で送る。
     *
     * ResponseBodyEmitter：send() するたびに、その分をすぐ画面に送る仕組み。
     * AI の回答を待つ処理は taskExecutor（裏の処理）で動かし、届いた分から send() する。
     */
    private ResponseEntity<ResponseBodyEmitter> streamAnswer(String system, List<Map<String, String>> messages) {
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(STREAM_TIMEOUT_MS);
        taskExecutor.execute(() -> {
            try {
                aiService.stream(system, messages, text -> sendLine(emitter, Map.of("text", text)));
                emitter.complete();
            } catch (AiService.AiException e) {
                try {
                    sendLine(emitter, Map.of("error", e.getMessage()));
                    emitter.complete();
                } catch (UncheckedIOException closed) {
                    emitter.complete();   // 画面がもう閉じられている
                }
            } catch (UncheckedIOException e) {
                emitter.complete();       // 画面を閉じた・別のページに移ったなど。続きは送らない
            } catch (RuntimeException e) {
                emitter.completeWithError(e);
            }
        });
        return ResponseEntity.ok().contentType(NDJSON).body(emitter);
    }

    /**
     * AI を呼ばずにエラーを返す（入力の誤りなど）。少しずつ返すときと同じ形（{"error": "..."} の1行）にする。
     */
    private ResponseEntity<ResponseBodyEmitter> streamError(String message) {
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(STREAM_TIMEOUT_MS);
        sendLine(emitter, Map.of("error", message));   // 返す前に send しても、返した後にまとめて送られる
        emitter.complete();
        return ResponseEntity.badRequest().contentType(NDJSON).body(emitter);
    }

    /** JSON を1行送る。文字化けしないよう、UTF-8 のバイト列にしてから送る */
    private void sendLine(ResponseBodyEmitter emitter, Map<String, String> data) {
        byte[] json = jsonMapper.writeValueAsBytes(data);
        byte[] line = new byte[json.length + 1];
        System.arraycopy(json, 0, line, 0, json.length);
        line[json.length] = '\n';
        try {
            emitter.send(line, MediaType.APPLICATION_OCTET_STREAM);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 画面から送られてきた「これまでの会話」を、AI に渡す形にする。新しいものから MAX_HISTORY 組だけ使う。
     */
    private static List<Map<String, String>> history(List<String> questions, List<String> answers) {
        List<Map<String, String>> messages = new ArrayList<>();
        if (questions == null || answers == null) {
            return messages;
        }
        int count = Math.min(questions.size(), answers.size());
        for (int i = Math.max(0, count - MAX_HISTORY); i < count; i++) {
            String q = truncate(questions.get(i), MAX_QUESTION_LENGTH);
            String a = truncate(answers.get(i), MAX_HISTORY_ANSWER_LENGTH);
            if (q.isEmpty() || a.isEmpty()) {
                continue;
            }
            messages.add(AiService.userMessage(q));
            messages.add(AiService.assistantMessage(a));
        }
        return messages;
    }

    private static String truncate(String text, int max) {
        String t = text == null ? "" : text.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private AppUser currentUser(Principal principal) {
        return userRepository.findByUsername(principal.getName()).orElseThrow();
    }

    /**
     * プロフィールの「アレルギー・苦手な食材」を AI に伝える文。登録が無ければ空文字。
     *
     * @param instruction その機能での扱い方（例：これらを使う料理は提案しないでください。）
     */
    private static String avoidNote(AppUser user, String instruction) {
        String avoid = user.getAvoidFoods();
        if (avoid == null || avoid.isBlank()) {
            return "";
        }
        return "\n\n<avoid>\n" + avoid.trim() + "\n</avoid>\n"
                + "<avoid> の中は、利用者のアレルギー・苦手な食材です。" + instruction;
    }

    /**
     * AI に渡す在庫。loadStock は数量が0より多い食材を期限が近い順に返す。期限切れは渡さない。
     */
    private List<Ingredient> usableStock(AppUser user) {
        return stockCheckService.loadStock(user).stream()
                .filter(i -> !i.isExpired())
                .limit(MAX_STOCK_ITEMS)
                .toList();
    }

    private String describeStock(List<Ingredient> stock) {
        return stock.stream().map(this::describe).collect(Collectors.joining("\n"));
    }

    /** リストの i 番目（無い・空なら ""）。フォームの分量・単位は空欄のことがあるため */
    private static String valueAt(List<String> list, int i) {
        return (list != null && i < list.size() && list.get(i) != null) ? list.get(i).trim() : "";
    }

    /**
     * AI の下書き（「説明：…」「作り方：」「1. …」の形）を、説明と手順のリストに分ける。
     * 小さいモデルは「**説明**：」のように飾りを付けることがあるので、* や # は取り除いてから読む。
     */
    static Map<String, Object> parseDraft(String text) {
        String description = "";
        List<String> steps = new ArrayList<>();
        for (String raw : text.lines().toList()) {
            String line = raw.replace("*", "").replace("#", "").trim();
            if (line.matches("^(説明|ひとこと)\\s*[:：].*")) {
                description = line.replaceFirst("^(説明|ひとこと)\\s*[:：]\\s*", "");
            } else if (line.matches("^\\d+\\s*[.．、)）].*")) {
                String step = line.replaceFirst("^\\d+\\s*[.．、)）]\\s*", "");
                if (!step.isEmpty()) {
                    steps.add(step);
                }
            }
        }
        return Map.of("description", description, "steps", steps);
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
    private String describe(Recipe recipe, int people) {
        // 倍率：レシピ詳細の人数切り替えと同じ計算（int 同士だと小数が消えるので double にする）
        double factor = (double) people / recipe.getServings();
        String ingredients = recipe.getIngredients().stream()
                .map(i -> "- " + i.getName() + " " + i.getQuantityText(factor)
                        + (i.getUnit() != null ? i.getUnit() : ""))
                .collect(Collectors.joining("\n"));

        StringBuilder sb = new StringBuilder();
        sb.append("料理名：").append(recipe.getTitle()).append("\n");
        sb.append("カテゴリ：").append(recipe.getCategory()).append("\n");
        if (people == recipe.getServings()) {
            sb.append("人数：").append(people).append("人分\n");
        } else {
            sb.append("人数：").append(people).append("人分（元のレシピは")
              .append(recipe.getServings()).append("人分。下の材料の分量は")
              .append(people).append("人分に換算済み）\n");
        }
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
