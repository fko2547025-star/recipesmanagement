package com.example.recipesmanagement.password;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;   // ★ 追加
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.recipesmanagement.common.RateLimiter;
import com.example.recipesmanagement.mypage.PasswordService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * パスワードの再設定。ログインしていなくても使える（SecurityConfig で許可する）。
 *
 * | URL                    | メソッド | 処理                         |
 * |------------------------|----------|------------------------------|
 * | /password/forgot       | GET      | ユーザーID・メールの入力画面 |
 * | /password/forgot       | POST     | 再設定のメールを送る         |
 * | /password/reset?token= | GET      | 新しいパスワードの入力画面   |
 * | /password/reset        | POST     | パスワードを変える           |
 */
@Controller
@RequestMapping("/password")
public class PasswordResetController {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetController.class);

    private final PasswordResetService resetService;
    private final RateLimiter rateLimiter;

    // 再設定のメールを送れる回数（1時間あたり）。他人のメールボックスにメールを大量に送りつけられないように
    public static final int MAIL_PER_USER_PER_HOUR = 3;    // 同じユーザー名あて
    public static final int MAIL_PER_IP_PER_HOUR = 10;     // 同じ場所（IP アドレス）から

    /*
     * メールのリンクの先頭（例：https://recipe.example.com）。application.properties の app.public-url。
     *
     * アクセスしてきた URL からは作らない。URL の「ホスト名」の部分は、送る側が自由に書き換えられるので、
     * 悪い人が他人の名前で申し込むと、その人に偽のサイトへのリンク（トークン付き）が届いてしまうため。
     * 設定が空のときは、メールを送らない（ログに警告を出す）。
     */
    @Value("${app.public-url:}")
    private String publicUrl;

    public PasswordResetController(PasswordResetService resetService, RateLimiter rateLimiter) {
        this.resetService = resetService;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/forgot")
    public String forgotForm() {
        return "password/forgot";
    }

    @PostMapping("/forgot")
    public String sendMail(@RequestParam String username,
                           @RequestParam String email,
                           HttpServletRequest request,
                           RedirectAttributes redirectAttributes) {

        if (!rateLimiter.tryAcquire("reset-ip", request.getRemoteAddr(), MAIL_PER_IP_PER_HOUR, Duration.ofHours(1))
                || !rateLimiter.tryAcquire("reset-user", username.trim().toLowerCase(), MAIL_PER_USER_PER_HOUR, Duration.ofHours(1))) {
            // 回数を超えたら送らない。画面の表示は同じにする（ユーザー名が本当にあるかを知られないように）
            log.info("パスワード再設定のメールの回数制限を超えたため、送りませんでした");
        } else if (publicUrl.isBlank()) {
            log.warn("app.public-url が設定されていないため、パスワード再設定のメールを送りませんでした");
        } else {
            // 最後の「/」は取る（https://example.com/ → https://example.com）
            resetService.requestReset(username, email, publicUrl.replaceAll("/+$", ""));
        }

        // 合っていても合っていなくても、同じ表示にする
        redirectAttributes.addFlashAttribute("sent", true);
        return "redirect:/password/forgot";   // PRG（再読み込みで、メールが2回送られないように）
    }

    @GetMapping("/reset")
    public String resetForm(@RequestParam(required = false) String token, Model model) {
        model.addAttribute("token", token);
        model.addAttribute("invalid", resetService.findUsable(token).isEmpty());
        model.addAttribute("minLength", PasswordService.MIN_LENGTH);
        return "password/reset";
    }

    @PostMapping("/reset")
    public String reset(@RequestParam String token,
                        @RequestParam String newPassword,
                        @RequestParam String confirmPassword,
                        Model model) {

        List<String> errors = resetService.reset(token, newPassword, confirmPassword);
        if (!errors.isEmpty()) {
            model.addAttribute("errors", errors);
            model.addAttribute("token", token);
            model.addAttribute("invalid", resetService.findUsable(token).isEmpty());
            model.addAttribute("minLength", PasswordService.MIN_LENGTH);
            return "password/reset";
        }
        return "redirect:/login?reset";
    }
}