package com.example.recipesmanagement.admin;

import java.security.Principal;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.recipesmanagement.mypage.PasswordService;
import com.example.recipesmanagement.user.PasswordRules;

/**
 * 管理者アカウントの管理と、自分のパスワードの変更（管理者画面）。
 * 管理者はマイページを使えないので、パスワードの変更はここで行う。
 *
 * | URL                        | メソッド | 処理                         |
 * |----------------------------|----------|------------------------------|
 * | /admin/admins              | GET      | 管理者の一覧と、追加の入力欄 |
 * | /admin/admins              | POST     | 管理者を追加                 |
 * | /admin/admins/{id}/delete  | POST     | 管理者を削除                 |
 * | /admin/account             | GET/POST | 自分のパスワードの変更       |
 */
@Controller
@RequestMapping("/admin")
public class AdminAccountController {

    private final AdminAccountService accountService;
    private final PasswordService passwordService;
    private final AdminLogService logService;
    private final com.example.recipesmanagement.user.EmailVerificationService verificationService;
    private final com.example.recipesmanagement.user.AppUserRepository userRepository;
    private final com.example.recipesmanagement.common.RateLimiter rateLimiter;

    public AdminAccountController(AdminAccountService accountService, PasswordService passwordService,
                                  AdminLogService logService,
                                  com.example.recipesmanagement.user.EmailVerificationService verificationService,
                                  com.example.recipesmanagement.user.AppUserRepository userRepository,
                                  com.example.recipesmanagement.common.RateLimiter rateLimiter) {
        this.accountService = accountService;
        this.passwordService = passwordService;
        this.logService = logService;
        this.verificationService = verificationService;
        this.userRepository = userRepository;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/admins")
    public String admins(Model model) {
        addAdminsAttributes(model);
        return "admin/admins";
    }

    @PostMapping("/admins")
    public String create(@RequestParam(defaultValue = "") String username,
                         @RequestParam(defaultValue = "") String email,
                         @RequestParam(defaultValue = "") String password,
                         @RequestParam(defaultValue = "") String passwordConfirm,
                         Principal principal, Model model, RedirectAttributes ra) {
        List<String> errors = accountService.create(username, email, password, passwordConfirm);
        if (!errors.isEmpty()) {
            model.addAttribute("errors", errors);
            model.addAttribute("username", username);   // パスワードは残さない
            model.addAttribute("email", email);
            addAdminsAttributes(model);
            return "admin/admins";
        }
        logService.record(principal, "管理者を追加", "@" + username.trim(), null);
        ra.addFlashAttribute("message", "管理者 @" + username.trim() + " を追加しました");
        return "redirect:/admin/admins";
    }

    @PostMapping("/admins/{id:\\d+}/delete")
    public String delete(@PathVariable Long id, Principal principal, RedirectAttributes ra) {
        String name = accountService.delete(id, principal.getName());
        logService.record(principal, "管理者を削除", "@" + name, null);
        ra.addFlashAttribute("message", "管理者 @" + name + " を削除しました");
        return "redirect:/admin/admins";
    }

    @GetMapping("/account")
    public String account(Principal principal, Model model) {
        addAccountAttributes(principal, model);
        return "admin/account";
    }

    /**
     * メールアドレスの確認メールを送る（管理者はマイページを使えないので、ここから送る）。
     * 確認が済むと、パスワードを忘れたときに再設定のメールを受け取れる。1時間に3回まで。
     */
    @PostMapping("/account/verify-email")
    public String sendVerification(Principal principal, RedirectAttributes ra) {
        if (rateLimiter.tryAcquire("verify-resend", principal.getName(), 3, java.time.Duration.ofHours(1))) {
            verificationService.resend(principal.getName());
            ra.addFlashAttribute("message", "確認のメールを送りました。メールのリンクを開いてください");
        } else {
            ra.addFlashAttribute("error", "短い時間にたくさん送っています。しばらくしてからもう一度お試しください");
        }
        return "redirect:/admin/account";
    }

    @PostMapping("/account")
    public String changePassword(@RequestParam(defaultValue = "") String currentPassword,
                                 @RequestParam(defaultValue = "") String newPassword,
                                 @RequestParam(defaultValue = "") String confirmPassword,
                                 Principal principal, Model model, RedirectAttributes ra) {
        List<String> errors = passwordService.change(principal.getName(), currentPassword, newPassword, confirmPassword);
        if (!errors.isEmpty()) {
            model.addAttribute("errors", errors);
            addAccountAttributes(principal, model);
            return "admin/account";
        }
        logService.record(principal, "自分のパスワードを変更", "@" + principal.getName(), null);
        ra.addFlashAttribute("message", "パスワードを変更しました");
        return "redirect:/admin/account";
    }

    private void addAccountAttributes(Principal principal, Model model) {
        model.addAttribute("minLength", PasswordRules.MIN_LENGTH);
        model.addAttribute("me", userRepository.findByUsername(principal.getName()).orElseThrow());
    }

    private void addAdminsAttributes(Model model) {
        model.addAttribute("admins", accountService.admins());
        model.addAttribute("minLength", PasswordRules.MIN_LENGTH);
    }
}
