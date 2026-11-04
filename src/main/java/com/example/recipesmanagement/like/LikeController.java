package com.example.recipesmanagement.like;

import java.security.Principal;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import com.example.recipesmanagement.user.AppUser;
import com.example.recipesmanagement.user.AppUserRepository;

/**
 * いいねを担当するコントローラー。
 *
 * | URL                | メソッド | 処理                 | ログイン |
 * |--------------------|----------|----------------------|----------|
 * | /recipes/{id}/like | POST     | いいね／取り消し     | 必要     |
 */
@Controller
public class LikeController {

    private final LikeService likeService;
    private final AppUserRepository userRepository;

    public LikeController(LikeService likeService, AppUserRepository userRepository) {
        this.likeService = likeService;
        this.userRepository = userRepository;
    }

    @PostMapping("/recipes/{id:\\d+}/like")
    public String toggle(@PathVariable Long id, Principal principal) {
        likeService.toggle(id, currentUser(principal));
        // #like：詳細画面の id="like" の場所までスクロールした状態で開く
        return "redirect:/recipes/" + id + "#like";
    }

    private AppUser currentUser(Principal principal) {
        return userRepository.findByUsername(principal.getName()).orElseThrow();
    }
}