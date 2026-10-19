package com.example.recipesmanagement.ingredient;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@RequestMapping("/ingredients")
public class IngredientController {

    private final IngredientRepository repository;

    public IngredientController(IngredientRepository repository) {
        this.repository = repository;
    }

    // 一覧（期限が近い順）
    @GetMapping
    public String list(Model model) {
        model.addAttribute("ingredients", repository.findAll(Sort.by("expiryDate")));
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


}