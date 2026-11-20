package com.example.recipesmanagement.contact;

import java.security.Principal;
import java.time.Duration;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.recipesmanagement.common.RateLimiter;
import com.example.recipesmanagement.user.AppUser;
import com.example.recipesmanagement.user.AppUserRepository;

import jakarta.servlet.http.HttpServletRequest;

/**
 * お問い合わせ。ログインしていなくても送れる（ログインできない人からの相談にも対応するため。SecurityConfig で許可）。
 *
 * | URL      | メソッド | 処理           |
 * |----------|----------|----------------|
 * | /contact | GET      | 入力画面       |
 * | /contact | POST     | 受け付ける     |
 */
@Controller
public class ContactController {

    // 同じ場所（IP アドレス）から1時間に送れる数（いたずらで大量に送られないように）
    public static final int PER_HOUR = 5;

    private final InquiryService inquiryService;
    private final AppUserRepository userRepository;
    private final RateLimiter rateLimiter;

    public ContactController(InquiryService inquiryService, AppUserRepository userRepository, RateLimiter rateLimiter) {
        this.inquiryService = inquiryService;
        this.userRepository = userRepository;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/contact")
    public String form(Principal principal, Model model) {
        // ログイン中なら、登録しているメールアドレスを最初から入れておく
        AppUser user = currentUser(principal);
        model.addAttribute("email", user != null ? user.getEmail() : null);
        model.addAttribute("category", "USAGE");
        addFormAttributes(model);
        return "contact";
    }

    @PostMapping("/contact")
    public String submit(@RequestParam(required = false) String category,
                         @RequestParam(required = false) String email,
                         @RequestParam(required = false) String message,
                         Principal principal,
                         HttpServletRequest request,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        List<String> errors = new java.util.ArrayList<>(inquiryService.validate(category, email, message));
        if (errors.isEmpty() && !rateLimiter.tryAcquire("contact", request.getRemoteAddr(), PER_HOUR, Duration.ofHours(1))) {
            errors.add("短い時間にたくさん送られています。しばらくしてからもう一度お試しください");
        }
        if (!errors.isEmpty()) {
            model.addAttribute("errors", errors);
            model.addAttribute("category", category);
            model.addAttribute("email", email);
            model.addAttribute("message", message);
            addFormAttributes(model);
            return "contact";
        }
        inquiryService.submit(category, email, message, currentUser(principal));
        redirectAttributes.addFlashAttribute("sent", true);
        return "redirect:/contact";   // PRG（再読み込みで2回送られないように）
    }

    private void addFormAttributes(Model model) {
        model.addAttribute("categories", Inquiry.CATEGORIES);
        model.addAttribute("maxLength", Inquiry.MAX_MESSAGE);
    }

    private AppUser currentUser(Principal principal) {
        return principal == null ? null : userRepository.findByUsername(principal.getName()).orElse(null);
    }
}
