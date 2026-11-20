package com.example.recipesmanagement.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;

import java.nio.charset.StandardCharsets;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 利用規約・プライバシーポリシーは、ログインしていなくても見られる。登録画面とログイン画面にもリンクがある。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LegalPagesTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    JavaMailSender mailSender;

    @Test
    void ログインしていなくても見られる() throws Exception {
        String privacy = mvc.perform(get("/privacy")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(privacy).contains("CookBuddy 運営チーム").contains("href=\"/contact\"");
        String terms = mvc.perform(get("/terms")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(terms).contains("してはいけないこと");
    }

    @Test
    void 読み込んでよい場所を決めるヘッダーが付く() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Content-Security-Policy", containsString("default-src 'self'")));
    }

    @Test
    void 登録画面とログイン画面にリンクがある() throws Exception {
        String register = mvc.perform(get("/register")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(register).contains("href=\"/terms\"").contains("同意したものとします");
        mvc.perform(get("/login")).andExpect(content().string(containsString("href=\"/privacy\"")));
    }
}
