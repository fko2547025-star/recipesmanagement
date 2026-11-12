package com.example.recipesmanagement.ingredient;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import com.example.recipesmanagement.user.AppUser;
import com.example.recipesmanagement.user.AppUserRepository;
import com.example.recipesmanagement.stock.ExpiringRecipeService;
import com.example.recipesmanagement.stock.StockCheckService;

@Controller
@RequestMapping("/ingredients")
public class IngredientController {

    private final IngredientRepository repository;
    private final AppUserRepository userRepository;
    private final StockCheckService stockCheckService;
    private final ExpiringRecipeService expiringRecipeService;

    public IngredientController(IngredientRepository repository,
                                AppUserRepository userRepository,
                                StockCheckService stockCheckService,
                                ExpiringRecipeService expiringRecipeService) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.stockCheckService = stockCheckService;
        this.expiringRecipeService = expiringRecipeService;
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
}