package com.example.recipesmanagement.recipe;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * レシピ関係の画面を担当するコントローラー。
 *
 * 今はレシピ一覧の仮ページを表示するだけ。
 * レシピの投稿・一覧・詳細は次のステップで作る。
 */
@Controller
@RequestMapping("/recipes")
public class RecipeController {

    /**
     * レシピ一覧を表示する。ログインしていなくても見られる（SecurityConfig で設定済み）。
     */
    @GetMapping
    public String list() {
        return "recipes/list";
    }
}