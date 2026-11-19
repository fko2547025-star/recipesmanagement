package com.example.recipesmanagement.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
 * 退会：本人のデータがすべて消え、ほかの人のデータは残ること。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountDeletionTest {

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

    @Value("${app.upload-dir}")
    String uploadDir;

    private Long createUser(String username) {
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode("Correct-pass1"));
        return users.save(user).getId();
    }

    private Long insert(String sql, Object... args) {
        org.springframework.jdbc.support.GeneratedKeyHolder keys = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement(sql, new String[] {"ID"});
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, keys);
        return keys.getKey().longValue();
    }

    @Test
    void パスワードが違うと退会できない() throws Exception {
        createUser("del-wrong");
        mvc.perform(post("/mypage/delete").with(csrf()).with(user("del-wrong")).param("password", "nope"))
                .andExpect(model().attribute("error", "パスワードが違います"));
        assertThat(users.existsByUsername("del-wrong")).isTrue();
    }

    @Test
    void 退会すると本人のデータがすべて消えほかの人のデータは残る() throws Exception {
        Long me = createUser("del-me");
        Long other = createUser("del-other");

        // 写真のファイル（消えることを確かめる）
        Path dir = Path.of(uploadDir);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("del-me-recipe.jpg"), "x");
        Files.writeString(dir.resolve("del-other-comment.jpg"), "x");

        Long myRecipe = insert("INSERT INTO recipes (user_id, title, category, steps, image_path) VALUES (?, 'A', 'MAIN', '焼く', 'del-me-recipe.jpg')", me);
        Long otherRecipe = insert("INSERT INTO recipes (user_id, title, category, steps) VALUES (?, 'B', 'MAIN', '煮る')", other);
        jdbc.update("INSERT INTO recipe_ingredients (recipe_id, name) VALUES (?, '卵')", myRecipe);
        // ほかの人が自分のレシピに付けた、いいね・コメント（写真付き）・通報
        jdbc.update("INSERT INTO likes (user_id, recipe_id) VALUES (?, ?)", other, myRecipe);
        Long otherComment = insert("INSERT INTO comments (user_id, recipe_id, content, image_path) VALUES (?, ?, 'おいしい', 'del-other-comment.jpg')", other, myRecipe);
        jdbc.update("INSERT INTO reports (reporter_id, comment_id, reason) VALUES (?, ?, 'test')", me, otherComment);
        // 自分がほかの人のレシピにしたこと
        jdbc.update("INSERT INTO likes (user_id, recipe_id) VALUES (?, ?)", me, otherRecipe);
        jdbc.update("INSERT INTO comments (user_id, recipe_id, content) VALUES (?, ?, 'いいね')", me, otherRecipe);
        jdbc.update("INSERT INTO reports (reporter_id, recipe_id, reason) VALUES (?, ?, 'test')", me, otherRecipe);
        // フォロー・献立・食材・再設定のリンク
        jdbc.update("INSERT INTO follows (follower_id, followee_id) VALUES (?, ?)", me, other);
        jdbc.update("INSERT INTO follows (follower_id, followee_id) VALUES (?, ?)", other, me);
        jdbc.update("INSERT INTO meal_plans (user_id, recipe_id) VALUES (?, ?)", me, otherRecipe);
        Long othersPlan = insert("INSERT INTO meal_plans (user_id, recipe_id) VALUES (?, ?)", other, myRecipe);
        jdbc.update("INSERT INTO ingredients (user_id, name) VALUES (?, '牛乳')", me);
        jdbc.update("INSERT INTO password_reset_tokens (user_id, token_hash, expires_at) VALUES (?, 'h', CURRENT_TIMESTAMP)", me);

        mvc.perform(post("/mypage/delete").with(csrf()).with(user("del-me")).param("password", "Correct-pass1"))
                .andExpect(redirectedUrl("/login?deleted"));

        assertThat(users.existsByUsername("del-me")).isFalse();
        for (String table : new String[] {"recipes", "likes", "comments", "meal_plans", "ingredients", "password_reset_tokens"}) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE user_id = ?", Integer.class, me))
                    .as(table).isZero();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM follows WHERE follower_id = ? OR followee_id = ?", Integer.class, me, me)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reports WHERE reporter_id = ?", Integer.class, me)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM likes WHERE recipe_id = ?", Integer.class, myRecipe)).isZero();
        // ほかの人のレシピ・献立は残る（献立のレシピの欄だけ空になる）
        assertThat(users.existsByUsername("del-other")).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recipes WHERE id = ?", Integer.class, otherRecipe)).isOne();
        assertThat(jdbc.queryForObject("SELECT recipe_id FROM meal_plans WHERE id = ?", Long.class, othersPlan)).isNull();
        // 写真のファイルも消えた
        assertThat(dir.resolve("del-me-recipe.jpg")).doesNotExist();
        assertThat(dir.resolve("del-other-comment.jpg")).doesNotExist();
    }
}
