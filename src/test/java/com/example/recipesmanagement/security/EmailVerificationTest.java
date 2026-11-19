package com.example.recipesmanagement.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.recipesmanagement.user.AppUserRepository;

/**
 * メールアドレスの確認：確認が済むまでパスワード再設定のメールが送られないこと。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmailVerificationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    AppUserRepository users;

    @MockitoBean
    JavaMailSender mailSender;

    private static String tokenIn(SimpleMailMessage mail) {
        Matcher m = Pattern.compile("/email/verify\\?token=([A-Za-z0-9_-]+)").matcher(mail.getText());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    private void forgot(String username, String email) throws Exception {
        mvc.perform(post("/password/forgot").with(csrf()).param("username", username).param("email", email))
                .andExpect(redirectedUrl("/password/forgot"));
    }

    @Test
    void 登録すると確認メールが届きリンクを開くまで再設定メールは送らない() throws Exception {
        mvc.perform(post("/register").with(csrf())
                        .param("username", "verify-me").param("email", "verify-me@example.com")
                        .param("password", "Passw0rd!x").param("passwordConfirm", "Passw0rd!x"))
                .andExpect(redirectedUrl("/login?registered"));

        // 確認メールが届く
        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(mail.capture());
        assertThat(mail.getValue().getTo()).containsExactly("verify-me@example.com");
        String token = tokenIn(mail.getValue());
        assertThat(users.findByUsername("verify-me").orElseThrow().isEmailVerified()).isFalse();

        // 確認前：再設定のメールは送らない
        clearInvocations(mailSender);
        forgot("verify-me", "verify-me@example.com");
        verify(mailSender, never()).send(any(SimpleMailMessage.class));

        // 違うトークンでは確認できない
        mvc.perform(get("/email/verify").param("token", "wrong")).andExpect(model().attribute("verified", false));

        // リンクを開く（ログインしていなくてよい）→ 確認済み
        mvc.perform(get("/email/verify").param("token", token)).andExpect(model().attribute("verified", true));
        assertThat(users.findByUsername("verify-me").orElseThrow().isEmailVerified()).isTrue();
        // 同じリンクは2回使えない
        mvc.perform(get("/email/verify").param("token", token)).andExpect(model().attribute("verified", false));

        // 確認後：再設定のメールを送る
        forgot("verify-me", "verify-me@example.com");
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void メールアドレスを変えると確認をやり直す() throws Exception {
        mvc.perform(post("/register").with(csrf())
                        .param("username", "change-mail").param("email", "old@example.com")
                        .param("password", "Passw0rd!x").param("passwordConfirm", "Passw0rd!x"))
                .andExpect(redirectedUrl("/login?registered"));
        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(mail.capture());
        mvc.perform(get("/email/verify").param("token", tokenIn(mail.getValue())));
        assertThat(users.findByUsername("change-mail").orElseThrow().isEmailVerified()).isTrue();

        clearInvocations(mailSender);
        mvc.perform(post("/mypage/profile").with(csrf()).with(user("change-mail"))
                        .param("nickname", "").param("email", "new@example.com").param("avoidFoods", ""))
                .andExpect(redirectedUrl("/mypage"));

        assertThat(users.findByUsername("change-mail").orElseThrow().isEmailVerified()).isFalse();
        verify(mailSender).send(mail.capture());
        assertThat(mail.getValue().getTo()).containsExactly("new@example.com");
    }
}
