package com.example.recipesmanagement.user;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthController {

    private final AppUserRepository repository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(AppUserRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    // ログイン画面
    @GetMapping("/login")
    public String login() {
        return "login";
    }

    // 新規登録画面
    @GetMapping("/register")
    public String registerForm() {
        return "register";
    }

    // 新規登録処理
    @PostMapping("/register")
    public String register(@RequestParam String username,
                        @RequestParam String password,
                        @RequestParam String passwordConfirm,   // ← 追加：確認用のパスワード
                        Model model) {

        // エラーで画面に戻ったとき、入力したユーザー名を残しておく（パスワードは安全のため残さない）
        model.addAttribute("username", username);

        // 確認用のパスワードと一致しなければ、登録画面に戻す
        // 文字列の比較は == ではなく equals()（== は「同じ物か」を比べてしまう）
        if (!password.equals(passwordConfirm)) {
            model.addAttribute("error", "パスワードが一致しません");
            return "register";
        }           
                         
        if (repository.existsByUsername(username)) {
            model.addAttribute("error", "そのユーザー名はすでに使われています");
            return "register";
        }

        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));   // 暗号化して保存
        repository.save(user);

        return "redirect:/login?registered";
    }
}