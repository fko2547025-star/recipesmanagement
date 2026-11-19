package com.example.recipesmanagement.user;

import java.security.Principal;
import java.util.List;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.recipesmanagement.common.ImageStorageService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 退会。ログイン必須。
 *
 * | URL            | メソッド | 処理                                  |
 * |----------------|----------|---------------------------------------|
 * | /mypage/delete | GET      | 確認画面（パスワードを入れてもらう）  |
 * | /mypage/delete | POST     | すべてのデータを消して、ログアウトする |
 */
@Controller
public class AccountDeletionController {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountDeletionService deletionService;
    private final ImageStorageService imageStorageService;

    public AccountDeletionController(AppUserRepository userRepository, PasswordEncoder passwordEncoder,
                                     AccountDeletionService deletionService, ImageStorageService imageStorageService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.deletionService = deletionService;
        this.imageStorageService = imageStorageService;
    }

    @GetMapping("/mypage/delete")
    public String confirm() {
        return "mypage/delete";
    }

    @PostMapping("/mypage/delete")
    public String delete(@RequestParam String password,
                         Principal principal,
                         HttpServletRequest request,
                         HttpServletResponse response,
                         Model model) {

        AppUser user = userRepository.findByUsername(principal.getName()).orElseThrow();
        // 本人か確かめる（ログインしたまま離れた席で、他人に退会されないように）
        if (!passwordEncoder.matches(password, user.getPassword())) {
            model.addAttribute("error", "パスワードが違います");
            return "mypage/delete";
        }

        List<String> images = deletionService.delete(user.getId());
        // データベースを消し終わってから、写真のファイルを消す
        images.forEach(imageStorageService::delete);

        // ログアウトする（セッションを捨てる）
        new SecurityContextLogoutHandler().logout(request, response, SecurityContextHolder.getContext().getAuthentication());
        return "redirect:/login?deleted";
    }
}
