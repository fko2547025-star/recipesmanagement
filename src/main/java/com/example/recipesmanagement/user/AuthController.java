package com.example.recipesmanagement.user;

import java.time.Duration;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.recipesmanagement.common.RateLimiter;

import jakarta.servlet.http.HttpServletRequest;

@Controller
public class AuthController {

    private final AppUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final RateLimiter rateLimiter;
    private final EmailVerificationService verificationService;

    // 同じ場所（IP アドレス）から1時間に作れるアカウントの数（大量にアカウントを作られないように）
    public static final int REGISTER_PER_HOUR = 5;

    public AuthController(AppUserRepository repository, PasswordEncoder passwordEncoder, RateLimiter rateLimiter,
                          EmailVerificationService verificationService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
        this.verificationService = verificationService;
    }

    // ログイン画面
    @GetMapping("/login")
    public String login(jakarta.servlet.http.HttpSession session, Model model) {
        // 利用停止でログアウトされたときは、その理由を1回だけ表示する（SuspendedUserInterceptor が入れている）
        Object reason = session.getAttribute(com.example.recipesmanagement.config.SuspendedUserInterceptor.REASON_KEY);
        if (reason != null) {
            model.addAttribute("suspendedReason", reason);
            session.removeAttribute(com.example.recipesmanagement.config.SuspendedUserInterceptor.REASON_KEY);
        }
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
                           @RequestParam String email,             // ← 追加：メールアドレス
                           @RequestParam String password,
                           @RequestParam String passwordConfirm,
                           HttpServletRequest request,
                           Model model) {

        // エラーで画面に戻ったとき、入力したユーザー名・メールアドレスを残しておく（パスワードは安全のため残さない）
        model.addAttribute("username", username);

        // ユーザー名：前後の空白を取り、長さと使える文字を確かめる
        // （HTML の maxlength などはブラウザ側のチェックで簡単に外せるので、サーバーでも必ず確かめる）
        username = UsernameRules.normalize(username);
        String usernameError = UsernameRules.check(username);
        if (usernameError != null) {
            model.addAttribute("error", usernameError);
            return "register";
        }

        // パスワード：長さ（8文字以上・72バイトまで）
        String passwordError = PasswordRules.check(password);
        if (passwordError != null) {
            model.addAttribute("error", passwordError);
            return "register";
        }
        model.addAttribute("email", email);                         // ← 追加

        // 確認用のパスワードと一致しなければ、登録画面に戻す
        // 文字列の比較は == ではなく equals()（== は「同じ物か」を比べてしまう）
        if (!password.equals(passwordConfirm)) {
            model.addAttribute("error", "パスワードが一致しません");
            return "register";
        }

        // ← 追加：メールアドレスのチェック（重複は許すので、形式だけ確かめる）
        String normalizedEmail = EmailRules.normalize(email);
        String emailError = EmailRules.check(normalizedEmail);
        if (emailError != null) {
            model.addAttribute("error", emailError);
            return "register";
        }

        if (repository.existsByUsername(username)) {
            model.addAttribute("error", "そのユーザー名はすでに使われています");
            return "register";
        }

        // 入力に問題が無く、登録する直前に数える（入力の間違いでは回数を使わない）
        if (!rateLimiter.tryAcquire("register", request.getRemoteAddr(), REGISTER_PER_HOUR, Duration.ofHours(1))) {
            model.addAttribute("error", "短い時間にたくさん登録されています。しばらくしてからもう一度お試しください");
            return "register";
        }

        AppUser user = new AppUser();
        user.setUsername(username);
        user.setEmail(normalizedEmail);                             // ← 追加
        user.setPassword(passwordEncoder.encode(password));   // 暗号化して保存
        verificationService.startVerification(user);          // 確認のメールを送る（確認が済むまでは未確認）
        repository.save(user);

        return "redirect:/login?registered";
    }
}