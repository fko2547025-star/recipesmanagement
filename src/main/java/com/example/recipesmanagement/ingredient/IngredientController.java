package com.example.recipesmanagement.ingredient;

import java.security.Principal;
import java.util.List;

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

@Controller
@RequestMapping("/ingredients")
public class IngredientController {

    private final IngredientRepository repository;
    private final AppUserRepository userRepository;

    public IngredientController(IngredientRepository repository, AppUserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
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
        model.addAttribute("username", principal.getName());
        return "ingredients/list";
    }

    // 登録画面を表示
    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("ingredient", new Ingredient());
        return "ingredients/form";
    }

    // 登録処理
    @PostMapping
    public String create(@ModelAttribute Ingredient ingredient, Principal principal) {
        ingredient.setUser(currentUser(principal));   // 持ち主をセット
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