package com.example.recipesmanagement.user;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class LoginUserDetailsService implements UserDetailsService {

    private final AppUserRepository repository;
    private final LoginAttemptService loginAttemptService;

    public LoginUserDetailsService(AppUserRepository repository, LoginAttemptService loginAttemptService) {
        this.repository = repository;
        this.loginAttemptService = loginAttemptService;
    }

    /** 今ログインしようとしている人の IP アドレス（LoginAttemptService が数えるのと同じ値） */
    private static String currentIp() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest().getRemoteAddr() : null;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser user = repository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("ユーザーが見つかりません: " + username));

        return User.withUsername(user.getUsername())
                .password(user.getPassword())
                // 役割（USER か ADMIN）。ADMIN だけが /admin/** を開ける（SecurityConfig）
                .roles(user.isAdmin() ? "ADMIN" : "USER")
                // 続けてログインに失敗してロック中なら、パスワードを確かめる前に断る（LockedException になる）
                .accountLocked(loginAttemptService.isLocked(username, currentIp()))
                .build();
    }
}