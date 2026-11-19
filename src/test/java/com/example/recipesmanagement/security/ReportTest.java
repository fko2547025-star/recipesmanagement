package com.example.recipesmanagement.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.recipesmanagement.user.AppUser;
import com.example.recipesmanagement.user.AppUserRepository;

/**
 * 通報と管理者の画面。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportTest {

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

    private Long createUser(String username) {
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode("Correct-pass1"));
        return users.save(user).getId();
    }

    private Long recipeOf(Long userId, String title) {
        jdbc.update("INSERT INTO recipes (user_id, title, category, steps) VALUES (?, ?, 'MAIN', '焼く')", userId, title);
        return jdbc.queryForObject("SELECT MAX(id) FROM recipes", Long.class);
    }

    @Test
    void 管理者でなければ通報の一覧は開けない() throws Exception {
        mvc.perform(get("/admin/reports").with(user("someone").roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin/reports").with(user("boss").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void 通報すると管理者の一覧に出て削除できる() throws Exception {
        Long author = createUser("rep-author");
        createUser("rep-reporter");
        Long recipeId = recipeOf(author, "<b>あやしいレシピ</b>");

        // 理由が空だと通報できない
        mvc.perform(post("/reports").with(csrf()).with(user("rep-reporter")).param("recipeId", recipeId.toString()).param("reason", " "))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reports", Integer.class)).isZero();

        mvc.perform(post("/reports").with(csrf()).with(user("rep-reporter"))
                        .param("recipeId", recipeId.toString()).param("reason", "迷惑な宣伝です"))
                .andExpect(redirectedUrl("/recipes/" + recipeId));

        // 管理者の一覧に出る（タイトルは HTML として動かないよう、エスケープされている）
        mvc.perform(get("/admin/reports").with(user("boss").roles("ADMIN")))
                .andExpect(content().string(Matchers.containsString("迷惑な宣伝です")))
                .andExpect(content().string(Matchers.containsString("&lt;b&gt;あやしいレシピ&lt;/b&gt;")));

        Long reportId = jdbc.queryForObject("SELECT MAX(id) FROM reports", Long.class);
        mvc.perform(post("/admin/reports/" + reportId + "/delete-target").with(csrf()).with(user("boss").roles("ADMIN")))
                .andExpect(redirectedUrl("/admin/reports"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recipes WHERE id = ?", Integer.class, recipeId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reports", Integer.class)).isZero();
    }

    @Test
    void ふつうのユーザーは通報されたものを消せない() throws Exception {
        Long author = createUser("rep-author2");
        Long recipeId = recipeOf(author, "ふつうのレシピ");
        jdbc.update("INSERT INTO reports (reporter_id, recipe_id, reason) VALUES (?, ?, 'x')", author, recipeId);
        Long reportId = jdbc.queryForObject("SELECT MAX(id) FROM reports", Long.class);

        mvc.perform(post("/admin/reports/" + reportId + "/delete-target").with(csrf()).with(user("rep-author2").roles("USER")))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recipes WHERE id = ?", Integer.class, recipeId)).isOne();
    }
}
