package com.example.recipesmanagement.ingredient;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequestMapping("/ingredients")
public class IngredientController {

    private final IngredientRepository repository;

    public IngredientController(IngredientRepository repository) {
        this.repository = repository;
    }

    // 一覧（カテゴリ絞り込み・並べ替え）
    @GetMapping
    public String list(@RequestParam(required = false) String category,
                    @RequestParam(defaultValue = "expiry") String sort,
                    Model model) {

        // 並び順を決める
        Sort order = switch (sort) {
            case "expiryDesc" -> Sort.by("expiryDate").descending();
            case "name"       -> Sort.by("name");
            case "newest"     -> Sort.by("id").descending();
            default           -> Sort.by("expiryDate");
        };

        // カテゴリが指定されていれば絞り込み、なければ全件
        List<Ingredient> ingredients;
        if (category == null || category.isEmpty()) {
            ingredients = repository.findAll(order);
        } else {
            ingredients = repository.findByCategory(category, order);
        }

        model.addAttribute("ingredients", ingredients);
        model.addAttribute("category", category);
        model.addAttribute("sort", sort);
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
    public String create(@ModelAttribute Ingredient ingredient) {
        repository.save(ingredient);
        return "redirect:/ingredients";
    }

    // 編集画面を表示
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Ingredient ingredient = repository.findById(id).orElseThrow();
        model.addAttribute("ingredient", ingredient);
        return "ingredients/form";
    }

    // 更新処理
    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @ModelAttribute Ingredient ingredient) {
        ingredient.setId(id);
        repository.save(ingredient);
        return "redirect:/ingredients";
    }

    // 削除処理
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        repository.deleteById(id);
        return "redirect:/ingredients";
    }

    @PostMapping("/{id}/quantity")
    public String changeQuantity(@PathVariable Long id, @RequestParam double quantity) {
        Ingredient ingredient = repository.findById(id).orElseThrow();
        ingredient.changeQuantity(quantity);
        repository.save(ingredient);
        return "redirect:/ingredients";
    }


}