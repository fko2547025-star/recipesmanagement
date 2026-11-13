package com.example.recipesmanagement.user;

import java.security.Principal;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;
import com.example.recipesmanagement.mypage.CookingRecordService;

import com.example.recipesmanagement.like.LikeRepository;
import com.example.recipesmanagement.recipe.RecipeRepository;

/**
 * 投稿者のページ（ほかの人のプロフィールと、投稿したレシピの一覧）。
 * レシピと同じく公開情報なので、ログインしていなくても見られる。
 *
 * | URL          | メソッド | 処理                       |
 * |--------------|----------|----------------------------|
 * | /users/{id}  | GET      | その人のプロフィールとレシピ |
 */
@Controller
public class UserPageController {

    private final AppUserRepository userRepository;
    private final RecipeRepository recipeRepository;
    private final LikeRepository likeRepository;
    private final CookingRecordService cookingRecordService;

    public UserPageController(AppUserRepository userRepository,
                              RecipeRepository recipeRepository,
                              LikeRepository likeRepository,
                              CookingRecordService cookingRecordService) {
        this.userRepository = userRepository;
        this.recipeRepository = recipeRepository;
        this.likeRepository = likeRepository;
        this.cookingRecordService = cookingRecordService;
    }

    /**
     * @param id 見たいユーザーの id（例：/users/3）
     * @param principal ログイン中のユーザー。ログインしていなければ null
     */
    @GetMapping("/users/{id:\\d+}")
    public String show(@PathVariable Long id, Principal principal, Model model) {
        // 存在しない id なら 404
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        // 自分のページを開いたときは、マイページへ（いいね・コメントのタブや、プロフィール編集があるので）
        if (principal != null && principal.getName().equals(user.getUsername())) {
            return "redirect:/mypage";
        }

        model.addAttribute("user", user);
        model.addAttribute("recipes", recipeRepository.findByUserOrderByIdDesc(user));
        model.addAttribute("postCount", recipeRepository.countByUser(user));
        model.addAttribute("receivedLikeCount", likeRepository.countByRecipe_User(user));
        model.addAttribute("likeCounts", likeRepository.countAllByRecipe());
        model.addAttribute("level", cookingRecordService.level(user));   // その人の料理レベル（称号）
        return "users/show";
    }
}