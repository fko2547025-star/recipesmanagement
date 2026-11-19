package com.example.recipesmanagement.comment;

import java.security.Principal;
import java.time.Duration;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.multipart.MultipartFile;

import com.example.recipesmanagement.common.RateLimiter;
import com.example.recipesmanagement.user.AppUser;
import com.example.recipesmanagement.user.AppUserRepository;

/**
 * コメントを担当するコントローラー。
 *
 * | URL                    | メソッド | 処理           | ログイン          |
 * |------------------------|----------|----------------|-------------------|
 * | /recipes/{id}/comments | POST     | コメント投稿   | 必要              |
 * | /comments/{id}/edit    | GET      | 編集画面       | 必要（本人のみ）  |
 * | /comments/{id}         | POST     | 更新           | 必要（本人のみ）  |
 * | /comments/{id}/delete  | POST     | 削除           | 必要（本人のみ）  |
 */
@Controller
public class CommentController {

    private final CommentService commentService;
    private final AppUserRepository userRepository;
    private final RateLimiter rateLimiter;

    // 1人が1分間に投稿できるコメントの数（大量の書き込みを防ぐ）
    public static final int COMMENTS_PER_MINUTE = 5;

    public CommentController(CommentService commentService, AppUserRepository userRepository, RateLimiter rateLimiter) {
        this.commentService = commentService;
        this.userRepository = userRepository;
        this.rateLimiter = rateLimiter;
    }

    /**
     * コメント（またはつくれぽ）を投稿する。
     *
     * @param image        name="image" の写真（選ばれていなければ空）
     * @param cookedReport 「作りました」のチェック。チェックが無いと送られてこないので、無ければ false
     */
    @PostMapping("/recipes/{id:\\d+}/comments")
    public String create(@PathVariable Long id,
                        @RequestParam String content,
                        @RequestParam(value = "image", required = false) MultipartFile image,
                        @RequestParam(defaultValue = "false") boolean cookedReport,
                        Principal principal,
                        RedirectAttributes redirectAttributes) {

        String error = commentService.validate(content, image);
        if (error == null
                && !rateLimiter.tryAcquire("comment", principal.getName(), COMMENTS_PER_MINUTE, Duration.ofMinutes(1))) {
            error = "短い時間にたくさん投稿されています。少し待ってからもう一度お試しください";
        }
        if (error != null) {
            // エラーメッセージと入力内容を、リダイレクト先の詳細画面に1回だけ渡す
            // （写真はリダイレクトで持ち越せないので、もう一度選んでもらう）
            redirectAttributes.addFlashAttribute("commentError", error);
            redirectAttributes.addFlashAttribute("commentDraft", content);
            redirectAttributes.addFlashAttribute("commentReport", cookedReport);
        } else {
            commentService.create(id, content, image, cookedReport, currentUser(principal));
        }
        return "redirect:/recipes/" + id + "#comments";
    }

    /**
     * コメント編集画面を表示する。本人以外は 403。
     */
    @GetMapping("/comments/{id:\\d+}/edit")
    public String editForm(@PathVariable Long id, Principal principal, Model model) {
        Comment comment = commentService.findOwned(id, currentUser(principal));
        model.addAttribute("comment", comment);
        model.addAttribute("content", comment.getContent());
        model.addAttribute("maxLength", Comment.MAX_LENGTH);
        return "comments/edit";
    }

    /**
     * コメントを更新する。本人以外は 403。
     */
    @PostMapping("/comments/{id:\\d+}")
    public String update(@PathVariable Long id,
                         @RequestParam String content,
                         Principal principal,
                         Model model) {

        AppUser user = currentUser(principal);
        Comment comment = commentService.findOwned(id, user);   // 先に本人確認

        String error = commentService.validate(content);
        if (error != null) {
            // 編集画面は redirect せずにそのまま表示するので、普通の model で渡せる
            model.addAttribute("comment", comment);
            model.addAttribute("content", content);
            model.addAttribute("maxLength", Comment.MAX_LENGTH);
            model.addAttribute("error", error);
            return "comments/edit";
        }

        Long recipeId = commentService.update(id, content, user);
        return "redirect:/recipes/" + recipeId + "#comments";
    }

    /**
     * コメントを削除する。本人以外は 403。
     */
    @PostMapping("/comments/{id:\\d+}/delete")
    public String delete(@PathVariable Long id, Principal principal) {
        Long recipeId = commentService.delete(id, currentUser(principal));
        return "redirect:/recipes/" + recipeId + "#comments";
    }

    private AppUser currentUser(Principal principal) {
        return userRepository.findByUsername(principal.getName()).orElseThrow();
    }
}