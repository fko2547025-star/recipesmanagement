package com.example.recipesmanagement.recipe;

import java.security.Principal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.example.recipesmanagement.user.AppUser;
import com.example.recipesmanagement.user.AppUserRepository;
import com.example.recipesmanagement.comment.CommentRepository;
import com.example.recipesmanagement.like.LikeRepository;
import com.example.recipesmanagement.stock.StockCheckItem;
import com.example.recipesmanagement.stock.StockCheckService;
import com.example.recipesmanagement.stock.IngredientNameMatcher;

/**
 * レシピ関係の画面を担当するコントローラー。
 *
 * | URL                  | メソッド | 処理       | ログイン          |
 * |----------------------|----------|------------|-------------------|
 * | /recipes             | GET      | 一覧表示   | 不要              |
 * | /recipes/{id}        | GET      | 詳細表示   | 不要              |
 * | /recipes/new         | GET      | 投稿画面   | 必要              |
 * | /recipes             | POST     | 投稿       | 必要              |
 * | /recipes/{id}/edit   | GET      | 編集画面   | 必要（本人のみ）  |
 * | /recipes/{id}        | POST     | 更新       | 必要（本人のみ）  |
 * | /recipes/{id}/delete | POST     | 削除       | 必要（本人のみ）  |
 *
 * 処理の中身は RecipeService に任せ、ここは「どの画面を出すか」に専念する。
 */
@Controller
@RequestMapping("/recipes")
public class RecipeController {

    private final RecipeRepository repository;
    private final RecipeService recipeService;
    private final AppUserRepository userRepository;
    private final LikeRepository likeRepository;
    private final CommentRepository commentRepository;
    private final StockCheckService stockCheckService;

    public RecipeController(RecipeRepository repository,
                            RecipeService recipeService,
                            AppUserRepository userRepository,
                            LikeRepository likeRepository,
                            CommentRepository commentRepository,
                            StockCheckService stockCheckService) {
        this.repository = repository;
        this.recipeService = recipeService;
        this.userRepository = userRepository;
        this.likeRepository = likeRepository;
        this.commentRepository = commentRepository;
        this.stockCheckService = stockCheckService;
    }

    /**
     * レシピ一覧を表示する（カテゴリと、料理名・材料での検索つき）。
     * 例：/recipes?category=肉&q=豚
     *
     * @param q 検索語（料理名または材料）
     */
    @GetMapping
    public String list(@RequestParam(required = false) String category,
                       @RequestParam(required = false) String q,
                       Model model) {

        boolean hasCategory = category != null && !category.isEmpty();
        // 空白を取り除いた検索語（「豚 汁」でも「豚汁」で探す）
        String keyword = IngredientNameMatcher.normalize(q);

        List<Recipe> recipes;
        if (!keyword.isEmpty()) {
            // 料理名・材料で探し、カテゴリも指定されていればさらに絞り込む
            recipes = repository.findByKeyword(keyword);
            if (hasCategory) {
                recipes = recipes.stream()
                        .filter(r -> category.equals(r.getCategory()))
                        .toList();
            }
        } else if (hasCategory) {
            recipes = repository.findByCategoryOrderByIdDesc(category);
        } else {
            recipes = repository.findAllByOrderByIdDesc();
        }

        model.addAttribute("recipes", recipes);
        model.addAttribute("categories", Recipe.CATEGORIES);        // カードを並べる順番
        model.addAttribute("categoryIcons", Recipe.CATEGORY_ICONS); // カテゴリ名 → 絵文字
        model.addAttribute("category", category);                   // 今選んでいるカテゴリ
        model.addAttribute("q", q);
        model.addAttribute("likeCounts", likeRepository.countAllByRecipe());   // id → いいね数                                 // 今の検索語
        return "recipes/list";
    }
    
    /**
     * レシピ詳細を表示する（いいね・コメント・在庫チェック・人数の切り替えつき）。
     *
     * @param servings 表示したい人数（例：/recipes/1?servings=4）。指定が無ければレシピの人数
     */
    @GetMapping("/{id:\\d+}")
    public String detail(@PathVariable Long id,
                         @RequestParam(required = false) Integer servings,
                         Principal principal,
                         Model model) {

        Recipe recipe = repository.findWithDetailsById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        // ----- 人数と倍率 -----
        // 1〜20 以外や、指定が無いときはレシピの人数にする
        int displayServings = (servings != null && servings >= 1 && servings <= 20)
                ? servings
                : recipe.getServings();
        // 倍率：int 同士の割り算だと小数が切り捨てられる（1 / 2 = 0）ので、double にしてから割る
        double factor = (double) displayServings / recipe.getServings();

        boolean liked = false;

        if (principal != null) {
            AppUser user = currentUser(principal);
            liked = likeRepository.existsByUserAndRecipe(user, recipe);

            List<StockCheckItem> stockCheck = stockCheckService.check(recipe, user, factor);
            model.addAttribute("stockCheck", stockCheck);
            model.addAttribute("missingCount", stockCheck.stream().filter(StockCheckItem::isProblem).count());
        }

        model.addAttribute("recipe", recipe);
        model.addAttribute("servings", displayServings);
        model.addAttribute("factor", factor);
        model.addAttribute("likeCount", likeRepository.countByRecipe(recipe));
        model.addAttribute("liked", liked);
        model.addAttribute("comments", commentRepository.findByRecipeOrderByIdAsc(recipe));
        return "recipes/detail";
    }

    // ===== 投稿 =====

    @GetMapping("/new")
    public String newForm(Model model) {
        RecipeForm form = new RecipeForm();
        form.setServings(2);
        form.ensureRows(3,2);
        return showForm(form, null, null, model);
    }

    @PostMapping
    public String create(@ModelAttribute("form") RecipeForm form,
                         @RequestParam(value = "image", required = false) MultipartFile image,
                         Principal principal,
                         Model model) {

        List<String> errors = validate(form, image);
        if (!errors.isEmpty()) {
            model.addAttribute("errors", errors);
            form.ensureRows(1,1);
            return showForm(form, null, null, model);
        }

        Recipe recipe = recipeService.create(form, image, currentUser(principal));
        return "redirect:/recipes/" + recipe.getId();
    }

    // ===== 編集・更新 =====

    /**
     * 編集画面を表示する。本人以外は 403。
     */
    @GetMapping("/{id:\\d+}/edit")
    public String editForm(@PathVariable Long id, Principal principal, Model model) {
        Recipe recipe = recipeService.findOwned(id, currentUser(principal));

        RecipeForm form = RecipeForm.from(recipe);   // 今の内容をフォームに詰める
        form.ensureRows(1,1);
        return showForm(form, id, recipe.getImagePath(), model);
    }

    /**
     * 更新処理。本人以外は 403。
     *
     * @param removeImage 「写真を削除する」のチェックボックス。チェックが無いと送られてこないので、
     *                    defaultValue = "false" で「送られてこなければ false」にしている
     */
    @PostMapping("/{id:\\d+}")
    public String update(@PathVariable Long id,
                         @ModelAttribute("form") RecipeForm form,
                         @RequestParam(value = "image", required = false) MultipartFile image,
                         @RequestParam(defaultValue = "false") boolean removeImage,
                         Principal principal,
                         Model model) {

        AppUser user = currentUser(principal);
        // 入力チェックより先に、本人のレシピか確認する（他人なら 403）
        Recipe current = recipeService.findOwned(id, user);

        List<String> errors = validate(form, image);
        if (!errors.isEmpty()) {
            model.addAttribute("errors", errors);
            form.ensureRows(1,1);
            return showForm(form, id, current.getImagePath(), model);
        }

        recipeService.update(id, form, image, removeImage, user);
        return "redirect:/recipes/" + id;
    }

    // ===== 削除 =====

    /**
     * 削除処理。本人以外は 403。
     */
    @PostMapping("/{id:\\d+}/delete")
    public String delete(@PathVariable Long id, Principal principal) {
        recipeService.delete(id, currentUser(principal));
        return "redirect:/recipes";
    }

    // ===== 内部で使う処理 =====

    /**
     * 投稿・編集フォームを表示する。
     *
     * @param recipeId     編集中のレシピの id。投稿のときは null
     * @param currentImage 今登録されている写真のファイル名。無ければ null
     */
    private String showForm(RecipeForm form, Long recipeId, String currentImage, Model model) {
        model.addAttribute("form", form);
        model.addAttribute("categories", Recipe.CATEGORIES);
        model.addAttribute("categoryIcons", Recipe.CATEGORY_ICONS);   // ← 追加：選択肢に絵文字を付けるため
        model.addAttribute("recipeId", recipeId);
        model.addAttribute("currentImage", currentImage);
        return "recipes/form";
    }

    /**
     * フォームの入力チェック＋写真の種類チェック。
     */
    private List<String> validate(RecipeForm form, MultipartFile image) {
        List<String> errors = form.validate();
        if (!recipeService.isAcceptableImage(image)) {
            errors.add("写真は JPEG・PNG・WebP・GIF のいずれかを選んでください");
        }
        return errors;
    }

    private AppUser currentUser(Principal principal) {
        return userRepository.findByUsername(principal.getName()).orElseThrow();
    }
}