package com.example.recipesmanagement.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.example.recipesmanagement.user.AppUser;
import com.example.recipesmanagement.user.AppUserRepository;

/**
 * 管理者画面：ユーザーの機能と管理者の機能が分かれていること、各機能が動くこと。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminFeaturesTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    AppUserRepository users;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    JavaMailSender mailSender;

    private static final RequestPostProcessor ADMIN = user("boss").roles("ADMIN");

    private AppUser createUser(String username, String role) {
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode("Correct-pass1"));
        user.setRole(role);
        return users.save(user);
    }

    private Long recipeOf(Long userId, String title) {
        jdbc.update("INSERT INTO recipes (user_id, title, category, steps) VALUES (?, ?, '和食', '焼く')", userId, title);
        return jdbc.queryForObject("SELECT MAX(id) FROM recipes", Long.class);
    }

    private String page(String url, RequestPostProcessor who) throws Exception {
        var req = get(url);
        if (who != null) {
            req.with(who);
        }
        return mvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    // ===== 分ける =====

    @Test
    void 管理者はユーザーの機能を使えずユーザーは管理者画面を開けない() throws Exception {
        Long owner = createUser("sep-owner", "USER").getId();
        Long recipeId = recipeOf(owner, "分ける確認");

        mvc.perform(get("/mypage").with(ADMIN)).andExpect(status().isForbidden());
        mvc.perform(get("/recipes/new").with(ADMIN)).andExpect(status().isForbidden());
        mvc.perform(post("/recipes/" + recipeId + "/comments").with(csrf()).with(ADMIN).param("content", "x"))
                .andExpect(status().isForbidden());
        // 見るだけならできる
        mvc.perform(get("/recipes/" + recipeId).with(ADMIN)).andExpect(status().isOk());

        mvc.perform(get("/admin").with(user("sep-owner"))).andExpect(status().isForbidden());
    }

    @Test
    void 管理者でログインすると管理者画面に移動する() throws Exception {
        createUser("login-admin", "ADMIN");
        createUser("login-user", "USER");
        mvc.perform(formLogin().user("login-admin").password("Correct-pass1")).andExpect(redirectedUrl("/admin"));
        mvc.perform(formLogin().user("login-user").password("Correct-pass1")).andExpect(redirectedUrl("/recipes"));
    }

    @Test
    void 管理者画面の各ページが開ける() throws Exception {
        for (String url : new String[] {"/admin", "/admin/users", "/admin/recipes", "/admin/recipes?featured=true",
                "/admin/comments", "/admin/reports", "/admin/inquiries", "/admin/inquiries?done=true",
                "/admin/announcements", "/admin/ng-words", "/admin/logs", "/admin/users?q=a", "/admin/admins", "/admin/account"}) {
            page(url, ADMIN);
        }
    }

    // ===== 利用停止 =====

    @Test
    void 利用停止にするとログアウトされ理由が表示され操作が記録される() throws Exception {
        AppUser target = createUser("bad-user", "USER");
        if (!users.existsByUsername("boss")) {
            createUser("boss", "ADMIN");
        }

        mvc.perform(post("/admin/users/" + target.getId() + "/suspend").with(csrf()).with(ADMIN).param("reason", "宣伝の繰り返し"))
                .andExpect(redirectedUrl("/admin/users"));
        assertThat(users.findByUsername("bad-user").orElseThrow().isSuspended()).isTrue();

        // 利用停止中のユーザーが画面を開くと、ログアウトされてログイン画面へ
        // ログアウトで前のセッションは捨てられ、理由は新しいセッションに入る（ブラウザなら Cookie で引き継がれる）
        var result = mvc.perform(get("/mypage").with(user("bad-user"))).andExpect(redirectedUrl("/login?suspended")).andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        String login = mvc.perform(get("/login?suspended").session(session))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(login).contains("利用停止中").contains("宣伝の繰り返し");

        // 操作の記録
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_logs WHERE action = 'ユーザーを利用停止' AND target = '@bad-user'",
                Integer.class)).isOne();

        // 解除するとまた使える
        mvc.perform(post("/admin/users/" + target.getId() + "/unsuspend").with(csrf()).with(ADMIN));
        mvc.perform(get("/mypage").with(user("bad-user"))).andExpect(status().isOk());
    }

    @Test
    void 管理者は利用停止にできない() throws Exception {
        AppUser other = createUser("other-admin", "ADMIN");
        mvc.perform(post("/admin/users/" + other.getId() + "/suspend").with(csrf()).with(ADMIN).param("reason", "x"))
                .andExpect(status().isBadRequest());
    }

    // ===== おすすめ・お知らせ =====

    @Test
    void おすすめにするとホームに並びお知らせは表示中だけ出る() throws Exception {
        Long owner = createUser("feat-owner", "USER").getId();
        Long recipeId = recipeOf(owner, "特製オムライス");

        mvc.perform(post("/admin/recipes/" + recipeId + "/feature").with(csrf()).with(ADMIN));
        assertThat(page("/recipes", null)).contains("おすすめレシピ").contains("特製オムライス");

        mvc.perform(post("/admin/announcements").with(csrf()).with(ADMIN).param("message", "今夜はメンテナンスします"));
        assertThat(page("/recipes", null)).contains("今夜はメンテナンスします");
        Long id = jdbc.queryForObject("SELECT MAX(id) FROM announcements", Long.class);
        mvc.perform(post("/admin/announcements/" + id + "/toggle").with(csrf()).with(ADMIN));
        assertThat(page("/recipes", null)).doesNotContain("今夜はメンテナンスします");
    }

    // ===== NGワード =====

    @Test
    void NGワードを含むコメントとニックネームは保存できない() throws Exception {
        Long owner = createUser("ng-owner", "USER").getId();
        createUser("ng-writer", "USER");
        Long recipeId = recipeOf(owner, "NG確認");
        mvc.perform(post("/admin/ng-words").with(csrf()).with(ADMIN).param("word", "ばかやろう"));

        // 全角・半角や大文字・小文字の違いでも止まるか（ここではそのまま）
        mvc.perform(post("/recipes/" + recipeId + "/comments").with(csrf()).with(user("ng-writer"))
                        .param("content", "この ばかやろう め"))
                .andExpect(flash().attributeExists("commentError"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comments WHERE recipe_id = ?", Integer.class, recipeId)).isZero();

        mvc.perform(post("/mypage/profile").with(csrf()).with(user("ng-writer"))
                        .param("nickname", "ばかやろう太郎").param("email", "").param("avoidFoods", ""))
                .andExpect(model().attributeExists("errors"));
    }

    // ===== お問い合わせ =====

    @Test
    void ログインしていなくても問い合わせでき管理者が返信できる() throws Exception {
        mvc.perform(post("/contact").with(csrf())
                        .param("category", "BUG").param("email", "guest@example.com").param("message", "写真が出ません"))
                .andExpect(redirectedUrl("/contact"));
        Long id = jdbc.queryForObject("SELECT MAX(id) FROM inquiries", Long.class);
        assertThat(page("/admin/inquiries", ADMIN)).contains("写真が出ません");

        mvc.perform(post("/admin/inquiries/" + id + "/reply").with(csrf()).with(ADMIN).param("reply", "直しました"))
                .andExpect(redirectedUrl("/admin/inquiries"));
        verify(mailSender).send(any(SimpleMailMessage.class));
        assertThat(jdbc.queryForObject("SELECT status FROM inquiries WHERE id = ?", String.class, id)).isEqualTo("DONE");
    }

    @Test
    void 問い合わせの入力が足りないと受け付けない() throws Exception {
        mvc.perform(post("/contact").with(csrf()).param("category", "BUG").param("email", "bad").param("message", ""))
                .andExpect(model().attributeExists("errors"));
    }

    // ===== 投稿の管理 =====

    @Test
    void 管理者はレシピとコメントを消せて記録が残る() throws Exception {
        Long owner = createUser("del-owner2", "USER").getId();
        Long recipeId = recipeOf(owner, "消されるレシピ");
        jdbc.update("INSERT INTO comments (user_id, recipe_id, content) VALUES (?, ?, '消されるコメント')", owner, recipeId);
        Long commentId = jdbc.queryForObject("SELECT MAX(id) FROM comments", Long.class);

        String found = mvc.perform(get("/admin/comments").param("q", "消される").with(ADMIN))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(found).contains("消されるコメント");
        mvc.perform(post("/admin/comments/" + commentId + "/delete").with(csrf()).with(ADMIN));
        mvc.perform(post("/admin/recipes/" + recipeId + "/delete").with(csrf()).with(ADMIN));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recipes WHERE id = ?", Integer.class, recipeId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_logs WHERE action IN ('レシピを削除', 'コメントを削除')",
                Integer.class)).isGreaterThanOrEqualTo(2);
    }

    // ===== 管理者の追加・削除・パスワード =====

    @Test
    void 管理者画面から管理者を追加でき追加した管理者でログインできる() throws Exception {
        if (!users.existsByUsername("boss")) {   // ほかのテストで作られていることもある
            createUser("boss", "ADMIN");
        }
        mvc.perform(post("/admin/admins").with(csrf()).with(ADMIN)
                        .param("username", "new-admin").param("email", "")
                        .param("password", "Admin-pass1").param("passwordConfirm", "Admin-pass1"))
                .andExpect(redirectedUrl("/admin/admins"));
        assertThat(users.findByUsername("new-admin").orElseThrow().isAdmin()).isTrue();
        mvc.perform(formLogin().user("new-admin").password("Admin-pass1")).andExpect(redirectedUrl("/admin"));

        // 確認のパスワードが違うと追加しない
        mvc.perform(post("/admin/admins").with(csrf()).with(ADMIN)
                        .param("username", "bad-admin").param("password", "Admin-pass1").param("passwordConfirm", "other-pass1"))
                .andExpect(model().attributeExists("errors"));
        assertThat(users.existsByUsername("bad-admin")).isFalse();

        // 自分自身は削除できない・ほかの管理者は削除できる
        Long me = users.findByUsername("boss").orElseThrow().getId();
        mvc.perform(post("/admin/admins/" + me + "/delete").with(csrf()).with(ADMIN)).andExpect(status().isBadRequest());
        Long other = users.findByUsername("new-admin").orElseThrow().getId();
        mvc.perform(post("/admin/admins/" + other + "/delete").with(csrf()).with(ADMIN)).andExpect(redirectedUrl("/admin/admins"));
        assertThat(users.existsByUsername("new-admin")).isFalse();
    }

    @Test
    void 管理者は自分のパスワードを変えられ管理者のプロフィールは見せない() throws Exception {
        AppUser admin = createUser("pw-admin", "ADMIN");
        mvc.perform(post("/admin/account").with(csrf()).with(user("pw-admin").roles("ADMIN"))
                        .param("currentPassword", "Correct-pass1").param("newPassword", "Changed-pass1").param("confirmPassword", "Changed-pass1"))
                .andExpect(redirectedUrl("/admin/account"));
        mvc.perform(formLogin().user("pw-admin").password("Changed-pass1")).andExpect(redirectedUrl("/admin"));

        mvc.perform(get("/users/" + admin.getId())).andExpect(status().isNotFound());
    }
}
