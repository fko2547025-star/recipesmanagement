package com.example.recipesmanagement.mypage;

import java.security.Principal;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.recipesmanagement.recipe.Recipe;
import com.example.recipesmanagement.recipe.RecipeRepository;
import com.example.recipesmanagement.user.AppUser;
import com.example.recipesmanagement.user.AppUserRepository;

/**
 * マイページを担当するコントローラー。ログイン必須（SecurityConfig の anyRequest）。
 *
 * | URL                     | メソッド | 処理                     |
 * |-------------------------|----------|--------------------------|
 * | /mypage                 | GET      | 投稿したレシピ           |
 * | /mypage?tab=liked       | GET      | いいねしたレシピ         |
 * | /mypage?tab=commented   | GET      | コメントしたレシピ       |
 */
@Controller
public class MyPageController {

    // 受け付けるタブ（これ以外が来たら「投稿したレシピ」にする）
    private static final List<String> TABS = List.of("posted", "liked", "commented");

    private final RecipeRepository recipeRepository;
    private final AppUserRepository userRepository;

    public MyPageController(RecipeRepository recipeRepository, AppUserRepository userRepository) {
        this.recipeRepository = recipeRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/mypage")
    public String mypage(@RequestParam(defaultValue = "posted") String tab,
                         Principal principal,
                         Model model) {

        if (!TABS.contains(tab)) {
            tab = "posted";
        }

        AppUser user = currentUser(principal);

        // 選ばれたタブのレシピだけを取得する
        List<Recipe> recipes = switch (tab) {
            case "liked"     -> recipeRepository.findLikedBy(user);
            case "commented" -> recipeRepository.findCommentedBy(user);
            default          -> recipeRepository.findByUserOrderByIdDesc(user);
        };

        model.addAttribute("tab", tab);          // どのタブを選択中か
        model.addAttribute("recipes", recipes);
        return "mypage/index";
    }

    private AppUser currentUser(Principal principal) {
        return userRepository.findByUsername(principal.getName()).orElseThrow();
    }
}