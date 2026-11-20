package com.example.recipesmanagement.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // ページごとのアクセス制限（上から順に判定される）
            .authorizeHttpRequests(auth -> auth
                // 新規登録・ログイン画面は誰でも
                .requestMatchers("/login", "/register", "/password/**", "/email/verify", "/privacy", "/terms", "/contact", "/error", "/css/**", "/images/**").permitAll()
                // CSS・画像・アップロード写真は、ログインしていなくても読み込めるようにする
                .requestMatchers("/css/**", "/images/**", "/uploads/**", "/js/**").permitAll()
                // 管理者の画面は ADMIN だけ
                .requestMatchers("/admin/**").hasRole("ADMIN")
                // レシピ投稿画面はユーザーだけ（下の /recipes/* より先に書く）
                .requestMatchers("/recipes/new").hasRole("USER")
                // レシピ一覧・詳細・ランキングは「見るだけ」なら誰でも（管理者も見られる）
                .requestMatchers(HttpMethod.GET, "/", "/recipes", "/recipes/*", "/ranking", "/users/*", "/users/*/followers", "/users/*/following").permitAll()
                // それ以外（投稿・いいね・コメント・マイページ・献立・食材管理）は、ふつうのユーザーだけ。
                // 管理者は ADMIN の役割しか持たないので、ここで断られる（ユーザーの機能と管理者の機能を分ける）
                .anyRequest().hasRole("USER")
            )
            // ログイン画面の設定
            .formLogin(form -> form
                .loginPage("/login")               // 自作のログイン画面を使う
                // ログイン成功後の移動先：管理者は管理者画面、ユーザーはレシピ一覧（ログイン前に開こうとした画面があればそこ）
                .successHandler((request, response, authentication) -> {
                    boolean admin = authentication.getAuthorities().stream()
                            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
                    if (admin) {
                        response.sendRedirect(request.getContextPath() + "/admin");
                    } else {
                        SavedRequestAwareAuthenticationSuccessHandler handler = new SavedRequestAwareAuthenticationSuccessHandler();
                        handler.setDefaultTargetUrl("/recipes");
                        handler.onAuthenticationSuccess(request, response, authentication);
                    }
                })
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
                // CSP（Content-Security-Policy）：画面が読み込んでよい場所を決める。
                // 万一、悪い人のスクリプトが画面に入り込んでも、ほかのサイトから読み込んだり、ほかのサイトへ送ったりできないようにする。
                //   ふだんは自分のサイト（'self'）だけ。文字・アイコンだけ Google Fonts から読み込む。
                //   'unsafe-inline'：画面の中に直接書いた <script>・style・onclick を使っているので許可する
                //   blob:：写真を選んだときの、送信前の見本の表示（URL.createObjectURL）に使う
                .contentSecurityPolicy(csp -> csp.policyDirectives(String.join("; ",
                        "default-src 'self'",
                        "script-src 'self' 'unsafe-inline'",
                        "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
                        "font-src 'self' https://fonts.gstatic.com",
                        "img-src 'self' data: blob:",
                        "connect-src 'self'",
                        "object-src 'none'",
                        "base-uri 'self'",
                        "form-action 'self'",
                        "frame-ancestors 'self'")))
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