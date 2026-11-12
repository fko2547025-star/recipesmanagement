package com.example.recipesmanagement.mypage;

import java.security.Principal;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.recipesmanagement.recipe.Recipe;
import com.example.recipesmanagement.recipe.RecipeRepository;
import com.example.recipesmanagement.user.AppUser;
import com.example.recipesmanagement.user.AppUserRepository;
import com.example.recipesmanagement.comment.CommentRepository;
import com.example.recipesmanagement.like.LikeRepository;

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
    private final ProfileService profileService;
    private final LikeRepository likeRepository;
    private final CommentRepository commentRepository;

    public MyPageController(RecipeRepository recipeRepository, AppUserRepository userRepository, ProfileService profileService, LikeRepository likeRepository, CommentRepository commentRepository) {
        this.recipeRepository = recipeRepository;
        this.userRepository = userRepository;
        this.profileService = profileService;
        this.likeRepository = likeRepository;
        this.commentRepository = commentRepository;
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
        // ----- 数字のまとめ（投稿数・自分のレシピがもらったいいね・自分が書いたコメント数） -----
        model.addAttribute("postCount", recipeRepository.countByUser(user));
        model.addAttribute("receivedLikeCount", likeRepository.countByRecipe_User(user));
        model.addAttribute("commentCount", commentRepository.countByUser(user));

        // プロフィールのカード用（ニックネーム・写真）
        model.addAttribute("user", user);
        // レシピカードのいいね数（ホームと同じ）
        model.addAttribute("likeCounts", likeRepository.countAllByRecipe());        
        return "mypage/index";
    }

    private AppUser currentUser(Principal principal) {
        return userRepository.findByUsername(principal.getName()).orElseThrow();
    }

    /**
     * プロフィール編集画面を表示する。
     */
    @GetMapping("/mypage/profile")
    public String profileForm(Principal principal, Model model) {
        AppUser user = userRepository.findByUsername(principal.getName()).orElseThrow();
        model.addAttribute("user", user);
        model.addAttribute("nickname", user.getNickname());
        model.addAttribute("nicknameMax", ProfileService.NICKNAME_MAX);
        return "mypage/profile";
    }

    /**
     * プロフィールを更新する。
     *
     * @param avatar       name="avatar" のファイル（選ばれていなければ空）
     * @param removeAvatar 「写真を削除する」のチェック。チェックが無いと送られてこないので、無ければ false
     */
    @PostMapping("/mypage/profile")
    public String updateProfile(@RequestParam(required = false) String nickname,
                                @RequestParam(value = "avatar", required = false) MultipartFile avatar,
                                @RequestParam(defaultValue = "false") boolean removeAvatar,
                                Principal principal,
                                Model model,
                                RedirectAttributes redirectAttributes) {

        List<String> errors = profileService.validate(nickname, avatar);
        if (!errors.isEmpty()) {
            // エラーなら、入力した内容を持ったまま編集画面に戻す
            model.addAttribute("errors", errors);
            model.addAttribute("user", userRepository.findByUsername(principal.getName()).orElseThrow());
            model.addAttribute("nickname", nickname);
            model.addAttribute("nicknameMax", ProfileService.NICKNAME_MAX);
            return "mypage/profile";
        }

        profileService.update(principal.getName(), nickname, avatar, removeAvatar);

        // フラッシュ属性：リダイレクト先の画面で1回だけ使える値（マイページに「更新しました」を出す）
        redirectAttributes.addFlashAttribute("message", "プロフィールを更新しました");
        return "redirect:/mypage";
    }
}