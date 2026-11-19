package com.example.recipesmanagement.user;

import java.security.Principal;
import java.time.Duration;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.recipesmanagement.common.RateLimiter;

/**
 * メールアドレスの確認。
 *
 * | URL                 | メソッド | 処理                         | ログイン |
 * |---------------------|----------|------------------------------|----------|
 * | /email/verify?token=| GET      | メールのリンク：確認済みにする | 不要     |
 * | /email/resend       | POST     | 確認メールをもう一度送る     | 必要     |
 */
@Controller
public class EmailVerificationController {

    // 確認メールをもう一度送れる回数（1時間あたり）
    public static final int RESEND_PER_HOUR = 3;

    private final EmailVerificationService verificationService;
    private final RateLimiter rateLimiter;

    public EmailVerificationController(EmailVerificationService verificationService, RateLimiter rateLimiter) {
        this.verificationService = verificationService;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/email/verify")
    public String verify(@RequestParam(required = false) String token, Model model) {
        model.addAttribute("verified", verificationService.verify(token));
        return "email/verified";
    }

    @PostMapping("/email/resend")
    public String resend(Principal principal, RedirectAttributes redirectAttributes) {
        if (rateLimiter.tryAcquire("verify-resend", principal.getName(), RESEND_PER_HOUR, Duration.ofHours(1))) {
            verificationService.resend(principal.getName());
            redirectAttributes.addFlashAttribute("message", "確認のメールを送りました。メールのリンクを開いてください");
        } else {
            redirectAttributes.addFlashAttribute("message", "短い時間にたくさん送っています。しばらくしてからもう一度お試しください");
        }
        return "redirect:/mypage";
    }
}
