package com.example.recipesmanagement.config;

import java.security.Principal;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.example.recipesmanagement.user.AppUser;
import com.example.recipesmanagement.user.AppUserRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 利用停止中のユーザーを、すぐにログアウトさせる。
 *
 * 画面を開くたびに確かめるので、ログイン中に管理者が利用停止にした場合も、次の操作でログアウトされる。
 * ログアウトしたあと、ログイン画面に「利用停止中です（理由：…）」と出す。
 * （パスワードが合っていた人にだけ理由を見せるため、ログインの前ではなく、ログインしたあとに確かめる）
 */
@Component
public class SuspendedUserInterceptor implements HandlerInterceptor {

    // ログイン画面に理由を渡すための、セッションの名前
    public static final String REASON_KEY = "suspendedReason";

    private final AppUserRepository userRepository;

    public SuspendedUserInterceptor(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Principal principal = request.getUserPrincipal();
        if (principal == null) {
            return true;
        }
        AppUser user = userRepository.findByUsername(principal.getName()).orElse(null);
        if (user == null || !user.isSuspended()) {
            return true;
        }
        new SecurityContextLogoutHandler().logout(request, response, SecurityContextHolder.getContext().getAuthentication());
        String reason = user.getSuspendedReason();
        request.getSession(true).setAttribute(REASON_KEY, reason == null || reason.isBlank() ? "（理由の記載はありません）" : reason);
        response.sendRedirect(request.getContextPath() + "/login?suspended");
        return false;
    }
}
