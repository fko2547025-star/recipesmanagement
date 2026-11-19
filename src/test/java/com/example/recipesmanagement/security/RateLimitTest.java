package com.example.recipesmanagement.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.recipesmanagement.user.AppUser;
import com.example.recipesmanagement.user.AppUserRepository;

/**
 * 登録・パスワード再設定のメール・コメントの回数制限を、実際にリクエストを送って確かめる。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateLimitTest {

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

    private AppUser createUser(String username) {
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPassword(passwordEncoder.encode("Correct-pass1"));
        return users.save(user);
    }

    @Test
    void 同じ場所からは1時間に5人までしか登録できない() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/register").with(csrf()).with(r -> { r.setRemoteAddr("10.1.0.1"); return r; })
                            .param("username", "reg-" + i).param("email", "reg@example.com")
                            .param("password", "Passw0rd!x").param("passwordConfirm", "Passw0rd!x"))
                    .andExpect(redirectedUrl("/login?registered"));
        }
        mvc.perform(post("/register").with(csrf()).with(r -> { r.setRemoteAddr("10.1.0.1"); return r; })
                        .param("username", "reg-5").param("email", "reg@example.com")
                        .param("password", "Passw0rd!x").param("passwordConfirm", "Passw0rd!x"))
                .andExpect(model().attributeExists("error"));
        assertThat(users.existsByUsername("reg-5")).isFalse();
    }

    @Test
    void 同じユーザーあての再設定メールは1時間に3通まで() throws Exception {
        createUser("reset-many");
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/password/forgot").with(csrf()).with(r -> { r.setRemoteAddr("10.1.0.2"); return r; })
                            .param("username", "reset-many").param("email", "reset-many@example.com"))
                    .andExpect(redirectedUrl("/password/forgot"))
                    .andExpect(flash().attribute("sent", true));   // 断ったときも、表示は同じ
        }
        verify(mailSender, times(3)).send(any(SimpleMailMessage.class));
    }

    @Test
    void コメントは1分間に5件まで() throws Exception {
        AppUser owner = createUser("comment-many");
        jdbc.update("INSERT INTO recipes (user_id, title, category, steps) VALUES (?, 'テスト', 'MAIN', '焼く')", owner.getId());
        Long recipeId = jdbc.queryForObject("SELECT MAX(id) FROM recipes", Long.class);

        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/recipes/" + recipeId + "/comments").with(csrf()).with(user("comment-many"))
                            .param("content", "コメント" + i))
                    .andExpect(flash().attributeCount(0));
        }
        mvc.perform(post("/recipes/" + recipeId + "/comments").with(csrf()).with(user("comment-many"))
                        .param("content", "6件目"))
                .andExpect(flash().attributeExists("commentError"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comments WHERE recipe_id = ?", Integer.class, recipeId))
                .isEqualTo(5);
    }
}
