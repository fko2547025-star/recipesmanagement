package com.example.recipesmanagement.security;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.example.recipesmanagement.user.AppUser;
import com.example.recipesmanagement.user.AppUserRepository;

/**
 * app.ai.enabled=false（公開用の設定）のとき、AI の欄を出さず、直接送られても AI を呼ばない。
 */
@SpringBootTest(properties = "app.ai.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiDisabledTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    AppUserRepository users;

    @Autowired
    PasswordEncoder passwordEncoder;

    private void createUser(String username) {
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode("Passw0rd!x"));
        users.save(user);
    }

    @Test
    void 投稿フォームにAIで下書きのボタンが出ない() throws Exception {
        createUser("ivy-noai");
        mvc.perform(get("/recipes/new").with(user("ivy-noai")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("aiDraftButton"))));
    }

    @Test
    void 直接送られてもAIを呼ばずに断る() throws Exception {
        createUser("jack-noai");
        mvc.perform(post("/ai/draft-recipe").with(user("jack-noai")).with(csrf())
                        .param("title", "豚の生姜焼き"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("AI機能は使えません。"));
    }
}
