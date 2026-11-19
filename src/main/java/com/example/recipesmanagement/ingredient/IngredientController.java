package com.example.recipesmanagement.ingredient;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.recipesmanagement.user.AppUser;
import com.example.recipesmanagement.user.AppUserRepository;
import com.example.recipesmanagement.stock.CookableRecipeService;
import com.example.recipesmanagement.stock.ExpiringRecipeService;
import com.example.recipesmanagement.stock.StockCheckService;
import com.example.recipesmanagement.stock.CookableRecipe;
import com.example.recipesmanagement.stock.CookableRecipeService;
import com.example.recipesmanagement.mealplan.ShoppingItem;
import com.example.recipesmanagement.mealplan.ShoppingListService;

@Controller
@RequestMapping("/ingredients")
public class IngredientController {

    private final IngredientRepository repository;
    private final AppUserRepository userRepository;
    private final StockCheckService stockCheckService;
    private final ExpiringRecipeService expiringRecipeService;
    private final CookableRecipeService cookableRecipeService;
    private final ShoppingListService shoppingListService;

    public IngredientController(IngredientRepository repository,
                                AppUserRepository userRepository,
                                StockCheckService stockCheckService,
                                ExpiringRecipeService expiringRecipeService,
                                CookableRecipeService cookableRecipeService,
                                ShoppingListService shoppingListService) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.stockCheckService = stockCheckService;
        this.expiringRecipeService = expiringRecipeService;
        this.cookableRecipeService = cookableRecipeService;
        this.shoppingListService = shoppingListService;
    }

    /**
     * この Controller の画面すべてに、カテゴリとアイコンを渡す。
     * メソッドに @ModelAttribute("名前") を付けると、この Controller の画面を表示する前に毎回呼ばれ、
     * 戻り値が model.addAttribute("名前", 戻り値) されたのと同じになる。
     * → 一覧・登録・編集のそれぞれで addAttribute を書かなくて済む
     */
    @ModelAttribute("categories")
    public List<String> categories() {
        return Ingredient.CATEGORIES;
    }

    @ModelAttribute("categoryIcons")
    public Map<String, String> categoryIcons() {
        return Ingredient.CATEGORY_ICONS;
    }

    /**
     * 食材のフォームから、id（番号）と user（持ち主）は受け取らない。
     *
     * @ModelAttribute Ingredient は、送られてきた値をそのまま食材に入れてしまう。
     * 画面に無い id を付けて送られると、他人の食材を上書きして自分のものにできてしまうため、
     * この2つは送られてきても無視する（id はデータベースが、持ち主はログイン中のユーザーから決める）。
     * "ingredient"：@ModelAttribute Ingredient の名前（型の名前を小文字にしたもの）にだけ当てる
     */
    @InitBinder("ingredient")
    public void protectIngredientFields(WebDataBinder binder) {
        binder.setDisallowedFields("id", "user", "user.*");
    }
    
    // ログイン中のユーザーを取得
    private AppUser currentUser(Principal principal) {
        return userRepository.findByUsername(principal.getName()).orElseThrow();
    }

    // ログイン中のユーザーの食材を1件取得（他人の食材なら404）
    private Ingredient findOwned(Long id, Principal principal) {
        return repository.findByIdAndUser(id, currentUser(principal))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    // 一覧（カテゴリ絞り込み・並べ替え）
    @GetMapping
    public String list(@RequestParam(required = false) String category,
                       @RequestParam(defaultValue = "expiry") String sort,
                       Principal principal,
                       Model model) {

        Sort order = switch (sort) {
            case "expiryDesc" -> Sort.by("expiryDate").descending();
            case "name"       -> Sort.by("name");
            case "newest"     -> Sort.by("id").descending();
            default           -> Sort.by("expiryDate");
        };

        AppUser user = currentUser(principal);
        List<Ingredient> ingredients;
        if (category == null || category.isEmpty()) {
            ingredients = repository.findByUser(user, order);
        } else {
            ingredients = repository.findByUserAndCategory(user, category, order);
        }

        model.addAttribute("ingredients", ingredients);
        model.addAttribute("category", category);
        model.addAttribute("sort", sort);

        // ----- 期限が近い食材と、それを使えるレシピ -----
        // 一覧はカテゴリで絞り込まれていることがあるので、在庫全体から探す
        List<Ingredient> expiring = stockCheckService.loadStock(user).stream()
                .filter(Ingredient::isExpiringSoon)
                .toList();

        model.addAttribute("expiring", expiring);
        model.addAttribute("suggestions", expiringRecipeService.suggest(expiring));

        return "ingredients/list";
    }

    // 今の在庫で作れるレシピ（今すぐ作れる ／ あと1〜2品で作れる）
    @GetMapping("/cookable")
    public String cookable(Principal principal, Model model) {
        List<CookableRecipe> all = cookableRecipeService.find(currentUser(principal), 2);

        model.addAttribute("ready", all.stream().filter(CookableRecipe::isReady).toList());
        model.addAttribute("almost", all.stream().filter(c -> !c.isReady()).toList());
        return "ingredients/cookable";
    }

    // 登録画面を表示
    @GetMapping("/new")
    public String newForm(Model model) {
        Ingredient ingredient = new Ingredient();
        ingredient.setCategory("野菜");          // よく使うカテゴリを最初から選んでおく
        ingredient.setExpiryType("賞味期限");
        model.addAttribute("ingredient", ingredient);
        return "ingredients/form";
    }

    // 登録処理
    @PostMapping
    public String create(@ModelAttribute Ingredient ingredient, Principal principal) {
        ingredient.setId(null);                       // 念のため：必ず新しい食材として保存する
        ingredient.setUser(currentUser(principal));   // 持ち主をセット

        // 選択肢以外の値（画面を書き換えて送られたものなど）なら「その他」にする
        if (ingredient.getCategory() == null || !Ingredient.CATEGORIES.contains(ingredient.getCategory())) {
            ingredient.setCategory("その他");
        }

        repository.save(ingredient);
        return "redirect:/ingredients";
    }

    // 編集画面を表示
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Principal principal, Model model) {
        model.addAttribute("ingredient", findOwned(id, principal));
        return "ingredients/form";
    }

    // 更新処理
    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @ModelAttribute Ingredient form, Principal principal) {
        Ingredient ingredient = findOwned(id, principal);

        // フォームの内容を、DBから取ってきた食材に写す
        ingredient.setName(form.getName());
        ingredient.setQuantity(form.getQuantity());
        ingredient.setUnit(form.getUnit());
        ingredient.setCategory(form.getCategory());
        // 選択肢以外の値（画面を書き換えて送られたものなど）なら「その他」にする
        if (ingredient.getCategory() == null || !Ingredient.CATEGORIES.contains(ingredient.getCategory())) {
            ingredient.setCategory("その他");
        }
        ingredient.setExpiryType(form.getExpiryType());
        ingredient.setExpiryDate(form.getExpiryDate());
        ingredient.setOpenedDate(form.getOpenedDate());

        repository.save(ingredient);
        return "redirect:/ingredients";
    }

    // 削除処理
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, Principal principal) {
        repository.delete(findOwned(id, principal));
        return "redirect:/ingredients";
    }

    // 数量の変更
    @PostMapping("/{id}/quantity")
    public String changeQuantity(@PathVariable Long id, @RequestParam double quantity, Principal principal) {
        Ingredient ingredient = findOwned(id, principal);
        ingredient.changeQuantity(quantity);
        repository.save(ingredient);
        return "redirect:/ingredients";
    }

    // ===== 買い物リスト → 在庫に追加 =====

    /**
     * 確認画面。買い物リストでチェックしたものを、初期値を入れた状態で表示する。
     * 例：/ingredients/purchase?date=2026-11-09&picked=0&picked=3
     *
     * @param date   買い物リストの週（月曜日）
     * @param picked チェックした行の番号（買い物リストの「買うもの」の上から 0, 1, 2 …）。何もチェックしていなければ null
     */
    @GetMapping("/purchase")
    public String purchaseForm(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                            @RequestParam(required = false) List<Integer> picked,
                            Principal principal,
                            Model model,
                            RedirectAttributes redirectAttributes) {

        if (picked == null || picked.isEmpty()) {
            redirectAttributes.addFlashAttribute("purchaseError", "在庫に追加するものにチェックを付けてください");
            return "redirect:/meal-plans/shopping?date=" + date;
        }

        // 買い物リストを作り直して、「買うもの」だけにしぼる（画面と同じ並び順になる）
        List<ShoppingItem> toBuy = shoppingListService.build(currentUser(principal), date).stream()
                .filter(ShoppingItem::isToBuy)
                .toList();

        PurchaseForm form = new PurchaseForm();
        for (Integer i : picked) {
            if (i != null && i >= 0 && i < toBuy.size()) {   // 範囲外の番号は無視する
                ShoppingItem item = toBuy.get(i);
                form.addRow(item.getCheck().getName(), item.getBuyText());
            }
        }

        model.addAttribute("form", form);
        model.addAttribute("date", date);
        return "ingredients/purchase";
    }

    /**
     * まとめて登録する。全部の行に問題が無いときだけ、全部を保存する。
     */
    @PostMapping("/bulk")
    public String createBulk(@ModelAttribute("form") PurchaseForm form,
                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                            Principal principal,
                            Model model,
                            RedirectAttributes redirectAttributes) {

        List<String> errors = form.validate();
        if (!errors.isEmpty()) {
            // エラーなら、入力した内容を持ったまま確認画面に戻す
            model.addAttribute("errors", errors);
            model.addAttribute("date", date);
            return "ingredients/purchase";
        }

        // saveAll：リストの中身をまとめて INSERT する
        List<Ingredient> saved = repository.saveAll(form.toIngredients(currentUser(principal)));

        redirectAttributes.addFlashAttribute("message", saved.size() + "件の食材を在庫に追加しました");
        return "redirect:/ingredients";
    }
}