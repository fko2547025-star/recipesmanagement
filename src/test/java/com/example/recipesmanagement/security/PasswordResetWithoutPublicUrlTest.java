package com.example.recipesmanagement.security;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.recipesmanagement.user.AppUser;
import com.example.recipesmanagement.user.AppUserRepository;

/**
 * app.public-url が空のときは、パスワード再設定のメールを送らない（アクセスしてきた URL からリンクを作らない）。
 */
@SpringBootTest(properties = "app.public-url=")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordResetWithoutPublicUrlTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    AppUserRepository users;

    @Autowired
    PasswordEncoder passwordEncoder;

    @MockitoBean
    JavaMailSender mailSender;

    @Test
    void 公開URLが設定されていなければメールを送らない() throws Exception {
        AppUser user = new AppUser();
        user.setUsername("henry-nourl");
        user.setEmail("henry@example.com");
        user.setPassword(passwordEncoder.encode("Passw0rd!x"));
        users.save(user);

        mvc.perform(post("/password/forgot").with(csrf())
                        .param("username", "henry-nourl")
                        .param("email", "henry@example.com"))
                .andExpect(status().is3xxRedirection());   // 画面の表示はいつもと同じ

        verifyNoInteractions(mailSender);
    }
}
