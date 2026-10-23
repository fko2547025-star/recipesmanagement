package com.example.recipesmanagement.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // ページごとのアクセス制限（上から順に判定される）
            .authorizeHttpRequests(auth -> auth
                // 新規登録・ログイン画面は誰でも
                .requestMatchers("/login", "/register", "/error").permitAll()
                // レシピ投稿画面はログイン必須（下の /recipes/* より先に書く）
                .requestMatchers("/recipes/new").authenticated()
                // レシピ一覧・詳細・ランキングは「見るだけ」なら誰でも
                .requestMatchers(HttpMethod.GET, "/", "/recipes", "/recipes/*", "/ranking", "/uploads/**").permitAll()
                // それ以外（投稿・いいね・コメント・マイページ・献立・食材管理）はログイン必須
                .anyRequest().authenticated()
            )
            // ログイン画面の設定
            .formLogin(form -> form
                .loginPage("/login")               // 自作のログイン画面を使う
                .defaultSuccessUrl("/recipes")  // ログイン成功後の移動先
                .permitAll()
            )
            // ログアウトの設定
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout")  // ログアウト後の移動先
                .permitAll()
            );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}