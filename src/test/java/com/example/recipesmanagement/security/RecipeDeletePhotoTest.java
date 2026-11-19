package com.example.recipesmanagement.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * レシピを消すと、そのレシピの写真だけでなく、コメントに付いていた写真のファイルも消えること。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecipeDeletePhotoTest {

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

    @Test
    void レシピを消すとコメントの写真のファイルも消える() throws Exception {
        Long owner = createUser("photo-owner");
        Long other = createUser("photo-commenter");

        Path dir = Path.of(uploadDir);
        Files.createDirectories(dir);
        Path recipePhoto = Files.writeString(dir.resolve("del-recipe-photo.jpg"), "x");
        Path commentPhoto = Files.writeString(dir.resolve("del-comment-photo.jpg"), "x");

        jdbc.update("INSERT INTO recipes (user_id, title, category, steps, image_path) VALUES (?, 'A', 'MAIN', '焼く', 'del-recipe-photo.jpg')", owner);
        Long recipeId = jdbc.queryForObject("SELECT MAX(id) FROM recipes", Long.class);
        // ほかの人が付けた、写真付きのコメント（つくれぽ）
        jdbc.update("INSERT INTO comments (user_id, recipe_id, content, image_path, cooked_report) VALUES (?, ?, '作りました', 'del-comment-photo.jpg', TRUE)", other, recipeId);

        mvc.perform(post("/recipes/" + recipeId + "/delete").with(csrf()).with(user("photo-owner")))
                .andExpect(status().is3xxRedirection());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recipes WHERE id = ?", Integer.class, recipeId)).isZero();
        assertThat(recipePhoto).doesNotExist();
        assertThat(commentPhoto).doesNotExist();   // 前は、このファイルが残っていた
    }
}
