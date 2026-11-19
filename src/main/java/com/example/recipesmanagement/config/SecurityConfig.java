package com.example.recipesmanagement.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // ページごとのアクセス制限（上から順に判定される）
            .authorizeHttpRequests(auth -> auth
                // 新規登録・ログイン画面は誰でも
                .requestMatchers("/login", "/register", "/password/**", "/email/verify", "/privacy", "/terms", "/error", "/css/**", "/images/**").permitAll()
                // CSS・画像・アップロード写真は、ログインしていなくても読み込めるようにする
                .requestMatchers("/css/**", "/images/**", "/uploads/**", "/js/**").permitAll()
                // レシピ投稿画面はログイン必須（下の /recipes/* より先に書く）
                .requestMatchers("/recipes/new").authenticated()
                // レシピ一覧・詳細・ランキングは「見るだけ」なら誰でも
                .requestMatchers(HttpMethod.GET, "/", "/recipes", "/recipes/*", "/ranking", "/users/*", "/users/*/followers", "/users/*/following").permitAll()
                // 管理者の画面は ADMIN だけ
                .requestMatchers("/admin/**").hasRole("ADMIN")
                // それ以外（投稿・いいね・コメント・マイページ・献立・食材管理）はログイン必須
                .anyRequest().authenticated()
            )
            // ログイン画面の設定
            .formLogin(form -> form
                .loginPage("/login")               // 自作のログイン画面を使う
                .defaultSuccessUrl("/recipes")  // ログイン成功後の移動先
                // ログインに失敗したとき：ロック中（続けて失敗した）なら ?locked、それ以外は ?error
                .failureHandler((request, response, exception) ->
                        response.sendRedirect(request.getContextPath()
                                + (exception instanceof LockedException ? "/login?locked" : "/login?error")))
                .permitAll()
            )
            // ほかのサイトへ移動・読み込みをするとき、今のページの URL を伝えない
            // （パスワード再設定の画面の URL にはトークンが入っているので、外のサイトに渡らないように）
            .headers(headers -> headers
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.SAME_ORIGIN))
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