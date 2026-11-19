package com.example.recipesmanagement.user;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class LoginUserDetailsService implements UserDetailsService {

    private final AppUserRepository repository;
    private final LoginAttemptService loginAttemptService;

    public LoginUserDetailsService(AppUserRepository repository, LoginAttemptService loginAttemptService) {
        this.repository = repository;
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser user = repository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("ユーザーが見つかりません: " + username));

        return User.withUsername(user.getUsername())
                .password(user.getPassword())
                .roles("USER")
                // 続けてログインに失敗してロック中なら、パスワードを確かめる前に断る（LockedException になる）
                .accountLocked(loginAttemptService.isLocked(username))
                .build();
    }
}