package com.example.recipesmanagement.common;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * プライバシーポリシーと利用規約の画面。ログインしていなくても見られる（SecurityConfig）。
 * 運営者名・問い合わせ先は SiteInfoAdvice が全画面に渡している。
 */
@Controller
public class LegalController {

    @GetMapping("/privacy")
    public String privacy() {
        return "legal/privacy";
    }

    @GetMapping("/terms")
    public String terms() {
        return "legal/terms";
    }
}
