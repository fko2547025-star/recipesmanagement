package com.example.recipesmanagement.comment;

import java.security.Principal;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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

    public CommentController(CommentService commentService, AppUserRepository userRepository) {
        this.commentService = commentService;
        this.userRepository = userRepository;
    }

    /**
     * コメントを投稿する。
     *
     * @param redirectAttributes リダイレクト先に値を渡すための入れ物
     */
    @PostMapping("/recipes/{id:\\d+}/comments")
    public String create(@PathVariable Long id,
                         @RequestParam String content,
                         Principal principal,
                         RedirectAttributes redirectAttributes) {

        String error = commentService.validate(content);
        if (error != null) {
            // エラーメッセージと入力内容を、リダイレクト先の詳細画面に1回だけ渡す
            redirectAttributes.addFlashAttribute("commentError", error);
            redirectAttributes.addFlashAttribute("commentDraft", content);
        } else {
            commentService.create(id, content, currentUser(principal));
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