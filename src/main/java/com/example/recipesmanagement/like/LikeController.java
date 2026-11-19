package com.example.recipesmanagement.like;

import java.security.Principal;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.example.recipesmanagement.user.AppUser;
import com.example.recipesmanagement.user.AppUserRepository;

/**
 * いいねを担当するコントローラー。
 *
 * | URL                | メソッド | 処理                 | ログイン |
 * |--------------------|----------|----------------------|----------|
 * | /recipes/{id}/like | POST     | いいね／取り消し     | 必要     |
 *
 * 同じ URL でも、JavaScript から JSON を求められたとき（Accept: application/json）は
 * 画面を移動せずに JSON（いいねの状態と数）を返す。一覧のカードの ♡ から使う。
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

    /**
     * 一覧のカードの ♡ から呼ばれる。画面は移動せず、切り替えた結果を返す。
     *
     * @return {"liked": true/false, "count": いいね数}
     */
    @PostMapping(value = "/recipes/{id:\\d+}/like", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> toggleJson(@PathVariable Long id, Principal principal) {
        boolean liked = likeService.toggle(id, currentUser(principal));
        return Map.of("liked", liked, "count", likeService.count(id));
    }

    private AppUser currentUser(Principal principal) {
        return userRepository.findByUsername(principal.getName()).orElseThrow();
    }
}