package com.example.recipesmanagement.follow;

import java.security.Principal;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

import com.example.recipesmanagement.user.AppUser;
import com.example.recipesmanagement.user.AppUserRepository;

/**
 * フォローを担当するコントローラー。
 *
 * | URL                    | メソッド | 処理                         | ログイン |
 * |------------------------|----------|------------------------------|----------|
 * | /users/{id}/follow     | POST     | フォローする／やめる         | 必要     |
 * | /users/{id}/followers  | GET      | その人のフォロワー一覧       | 不要     |
 * | /users/{id}/following  | GET      | その人のフォロー中一覧       | 不要     |
 */
@Controller
@RequestMapping("/users/{id:\\d+}")
public class FollowController {

    private final FollowService followService;
    private final AppUserRepository userRepository;

    public FollowController(FollowService followService, AppUserRepository userRepository) {
        this.followService = followService;
        this.userRepository = userRepository;
    }

    @PostMapping("/follow")
    public String toggle(@PathVariable Long id, Principal principal) {
        AppUser me = userRepository.findByUsername(principal.getName()).orElseThrow();
        followService.toggle(me, id);
        return "redirect:/users/" + id;   // その人のページに戻る（PRG）
    }

    @GetMapping("/followers")
    public String followers(@PathVariable Long id, Model model) {
        return showList(id, "followers", model);
    }

    @GetMapping("/following")
    public String following(@PathVariable Long id, Model model) {
        return showList(id, "following", model);
    }

    /**
     * フォロワー一覧・フォロー中一覧（同じ画面を使い、mode で切り替える）。
     */
    private String showList(Long id, String mode, Model model) {
        AppUser owner = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        model.addAttribute("owner", owner);
        model.addAttribute("mode", mode);
        model.addAttribute("users", mode.equals("followers")
                ? followService.followers(owner)
                : followService.following(owner));
        model.addAttribute("followerCount", followService.followerCount(owner));
        model.addAttribute("followingCount", followService.followingCount(owner));
        return "users/follows";
    }
}