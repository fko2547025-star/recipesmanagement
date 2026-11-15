package com.example.recipesmanagement.password;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.example.recipesmanagement.mypage.PasswordService;

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

    private final PasswordResetService resetService;

    public PasswordResetController(PasswordResetService resetService) {
        this.resetService = resetService;
    }

    @GetMapping("/forgot")
    public String forgotForm() {
        return "password/forgot";
    }

    @PostMapping("/forgot")
    public String sendMail(@RequestParam String username,
                           @RequestParam String email,
                           RedirectAttributes redirectAttributes) {

        // 今開いているサイトの先頭の URL（例：http://localhost:8081）。リンクを作るのに使う
        String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        resetService.requestReset(username, email, baseUrl);

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