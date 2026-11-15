package com.example.recipesmanagement.mypage;

import java.security.Principal;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.recipesmanagement.comment.CommentRepository;
import com.example.recipesmanagement.like.LikeRepository;
import com.example.recipesmanagement.recipe.Recipe;
import com.example.recipesmanagement.recipe.RecipeRepository;
import com.example.recipesmanagement.user.AppUser;
import com.example.recipesmanagement.user.AppUserRepository;
import com.example.recipesmanagement.follow.FollowService;

/**
 * マイページを担当するコントローラー。ログイン必須（SecurityConfig の anyRequest）。
 *
 * | URL                     | メソッド | 処理                     |
 * |-------------------------|----------|--------------------------|
 * | /mypage                 | GET      | 投稿したレシピ           |
 * | /mypage?tab=liked       | GET      | いいねしたレシピ         |
 * | /mypage?tab=commented   | GET      | コメントしたレシピ       |
 * | /mypage/profile         | GET/POST | プロフィール編集         |
 * | /mypage/password        | GET/POST | パスワードの変更         |
 */
@Controller
public class MyPageController {

    // 受け付けるタブ（これ以外が来たら「投稿したレシピ」にする）
    private static final List<String> TABS = List.of("posted", "liked", "commented");

    private final RecipeRepository recipeRepository;
    private final AppUserRepository userRepository;
    private final LikeRepository likeRepository;
    private final CommentRepository commentRepository;
    private final ProfileService profileService;
    private final PasswordService passwordService;
    private final CookingRecordService cookingRecordService;
    private final FollowService followService;

    public MyPageController(RecipeRepository recipeRepository,
                            AppUserRepository userRepository,
                            LikeRepository likeRepository,
                            CommentRepository commentRepository,
                            ProfileService profileService,
                            PasswordService passwordService,
                            CookingRecordService cookingRecordService,
                            FollowService followService) {
        this.recipeRepository = recipeRepository;
        this.userRepository = userRepository;
        this.likeRepository = likeRepository;
        this.commentRepository = commentRepository;
        this.profileService = profileService;
        this.passwordService = passwordService;
        this.cookingRecordService = cookingRecordService;
        this.followService = followService;
    }

    // ===== マイページ =====

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
        model.addAttribute("recipes", recipes);  // そのタブのレシピ一覧

        // 数字のまとめ（投稿数・自分のレシピがもらったいいね・自分が書いたコメント数）
        model.addAttribute("postCount", recipeRepository.countByUser(user));
        model.addAttribute("receivedLikeCount", likeRepository.countByRecipe_User(user));
        model.addAttribute("commentCount", commentRepository.countByUser(user));

        model.addAttribute("user", user);                                      // プロフィールのカード用
        model.addAttribute("likeCounts", likeRepository.countAllByRecipe());   // レシピカードのいいね数

        // 料理レベル（称号）と、今週の料理スタンプ
        model.addAttribute("level", cookingRecordService.level(user));
        model.addAttribute("stampCard", cookingRecordService.stampCard(user));
        // フォロワー・フォロー中の人数
        model.addAttribute("followerCount", followService.followerCount(user));
        model.addAttribute("followingCount", followService.followingCount(user));        
        return "mypage/index";
    }

    @GetMapping("/mypage/profile")
    public String profileForm(Principal principal, Model model) {
        AppUser user = currentUser(principal);
        addProfileFormAttributes(model, user, user.getNickname(), user.getEmail());
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
                                @RequestParam(required = false) String email,   // ← 追加
                                @RequestParam(value = "avatar", required = false) MultipartFile avatar,
                                @RequestParam(defaultValue = "false") boolean removeAvatar,
                                Principal principal,
                                Model model,
                                RedirectAttributes redirectAttributes) {

        AppUser user = currentUser(principal);

        List<String> errors = profileService.validate(nickname, email, avatar);
        if (!errors.isEmpty()) {
            model.addAttribute("errors", errors);
            addProfileFormAttributes(model, user, nickname, email);   // 入力した値を残して戻す
            return "mypage/profile";
        }

        profileService.update(principal.getName(), nickname, email, avatar, removeAvatar);

        redirectAttributes.addFlashAttribute("message", "プロフィールを更新しました");
        return "redirect:/mypage";
    }

    // ===== パスワードの変更 =====

    @GetMapping("/mypage/password")
    public String passwordForm(Model model) {
        model.addAttribute("minLength", PasswordService.MIN_LENGTH);
        return "mypage/password";
    }

    /**
     * パスワードを変更する。
     * パスワードは、エラーで画面に戻るときも入力欄に戻さない（安全のため、入力し直してもらう）。
     */
    @PostMapping("/mypage/password")
    public String changePassword(@RequestParam String currentPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 Principal principal,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {

        List<String> errors = passwordService.change(principal.getName(), currentPassword, newPassword, confirmPassword);
        if (!errors.isEmpty()) {
            model.addAttribute("errors", errors);
            model.addAttribute("minLength", PasswordService.MIN_LENGTH);
            return "mypage/password";
        }

        redirectAttributes.addFlashAttribute("message", "パスワードを変更しました");
        return "redirect:/mypage";
    }

    // ===== 内部で使う処理 =====

    private void addProfileFormAttributes(Model model, AppUser user, String nickname, String email) {
        model.addAttribute("user", user);
        model.addAttribute("nickname", nickname);
        model.addAttribute("email", email);
        model.addAttribute("nicknameMax", ProfileService.NICKNAME_MAX);
        model.addAttribute("level", cookingRecordService.level(user));
    }

    private AppUser currentUser(Principal principal) {
        return userRepository.findByUsername(principal.getName()).orElseThrow();
    }
}