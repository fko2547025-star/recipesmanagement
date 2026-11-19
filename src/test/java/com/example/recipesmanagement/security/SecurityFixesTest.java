package com.example.recipesmanagement.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Sort;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.recipesmanagement.ingredient.Ingredient;
import com.example.recipesmanagement.ingredient.IngredientRepository;
import com.example.recipesmanagement.user.AppUser;
import com.example.recipesmanagement.user.AppUserRepository;

/**
 * セキュリティの修正が効いているかを、実際にアプリへリクエストを送って確かめる。
 * データベースはメモリ上の空のもの（@ActiveProfiles("test")）。テストごとに別のユーザー名を使う。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityFixesTest {

    @Autowired
    MockMvc mvc;                         // アプリにリクエストを送る道具（サーバーは立てない）

    @Autowired
    AppUserRepository users;

    @Autowired
    IngredientRepository ingredients;

    @Autowired
    PasswordEncoder passwordEncoder;

    @MockitoBean
    JavaMailSender mailSender;           // 本当にはメールを送らず、送ろうとした中身を確かめる

    private AppUser createUser(String username, String password) {
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPassword(passwordEncoder.encode(password));
        return users.save(user);
    }

    // ===== 1. 他人の食材を乗っ取れない =====

    @Test
    void 食材の登録にidを付けて送っても他人の食材は変わらない() throws Exception {
        AppUser alice = createUser("alice-ing", "Passw0rd!x");
        AppUser bob = createUser("bob-ing", "Passw0rd!x");

        Ingredient wagyu = new Ingredient();
        wagyu.setUser(alice);
        wagyu.setName("高級和牛");
        wagyu.setQuantity(500.0);
        wagyu.setUnit("g");
        wagyu.setCategory("肉");
        ingredients.save(wagyu);

        // bob が、alice の食材の id と持ち主を付けて登録を送る
        mvc.perform(post("/ingredients").with(user("bob-ing")).with(csrf())
                        .param("id", wagyu.getId().toString())
                        .param("user.id", alice.getId().toString())
                        .param("name", "乗っ取り")
                        .param("quantity", "1")
                        .param("unit", "個")
                        .param("category", "その他"))
                .andExpect(status().is3xxRedirection());

        Ingredient after = ingredients.findById(wagyu.getId()).orElseThrow();
        assertThat(after.getName()).isEqualTo("高級和牛");               // alice の食材はそのまま
        assertThat(after.getUser().getId()).isEqualTo(alice.getId());
        assertThat(ingredients.findByUser(bob, Sort.unsorted()))         // bob には新しい食材が増えただけ
                .extracting(Ingredient::getName).containsExactly("乗っ取り");
    }

    // ===== 2. パスワード再設定のリンクは、設定の URL だけで作る =====

    @Test
    void 再設定のメールのリンクはHostを偽っても設定のURLになる() throws Exception {
        createUser("dave-reset", "Passw0rd!x");

        mvc.perform(post("/password/forgot").with(csrf())
                        .with(request -> {          // 悪い人が Host を偽って申し込んだことにする
                            request.setServerName("evil.example.com");
                            request.addHeader("Host", "evil.example.com");
                            return request;
                        })
                        .param("username", "dave-reset")
                        .param("email", "dave-reset@example.com"))
                .andExpect(status().is3xxRedirection());

        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(mail.capture());
        assertThat(mail.getValue().getText())
                .contains("http://localhost:8081/password/reset?token=")
                .doesNotContain("evil.example.com");
    }

    // ===== 4. ユーザー名・パスワードのチェック（画面を通さずに直接送っても） =====

    @Test
    void 短いパスワードでは登録できない() throws Exception {
        mvc.perform(post("/register").with(csrf())
                        .param("username", "weak-user")
                        .param("email", "weak@example.com")
                        .param("password", "a")
                        .param("passwordConfirm", "a"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attribute("error", containsString("8文字以上")));
        assertThat(users.existsByUsername("weak-user")).isFalse();
    }

    @Test
    void 空白だけや長すぎるユーザー名では登録できずエラーにもならない() throws Exception {
        for (String username : new String[] {"   ", "x".repeat(300), "はるき"}) {
            mvc.perform(post("/register").with(csrf())
                            .param("username", username)
                            .param("email", "u@example.com")
                            .param("password", "Passw0rd!x")
                            .param("passwordConfirm", "Passw0rd!x"))
                    .andExpect(status().isOk())                       // 500 にならない
                    .andExpect(view().name("register"))
                    .andExpect(model().attributeExists("error"));
        }
    }

    @Test
    void 正しい内容なら登録でき前後の空白は取られる() throws Exception {
        mvc.perform(post("/register").with(csrf())
                        .param("username", "  carol-ok  ")
                        .param("email", "carol@example.com")
                        .param("password", "Passw0rd!x")
                        .param("passwordConfirm", "Passw0rd!x"))
                .andExpect(redirectedUrl("/login?registered"));
        assertThat(users.existsByUsername("carol-ok")).isTrue();
    }

    // ===== 5. ログインの連続失敗でロック =====

    @Test
    void 続けて10回失敗すると正しいパスワードでもしばらくログインできない() throws Exception {
        createUser("erin-lock", "Correct-pass1");

        for (int i = 0; i < 10; i++) {
            mvc.perform(formLogin().user("erin-lock").password("wrong-" + i))
                    .andExpect(redirectedUrl("/login?error"));
        }
        mvc.perform(formLogin().user("erin-lock").password("Correct-pass1"))
                .andExpect(redirectedUrl("/login?locked"));
    }

    @Test
    void 他人が別の場所で失敗しても本人はロックされない() throws Exception {
        createUser("gina-lock", "Correct-pass1");

        // 攻撃する人（10.0.0.66）が10回失敗する
        for (int i = 0; i < 10; i++) {
            mvc.perform(loginFrom("gina-lock", "wrong-" + i, "10.0.0.66"))
                    .andExpect(redirectedUrl("/login?error"));
        }
        mvc.perform(loginFrom("gina-lock", "Correct-pass1", "10.0.0.66"))
                .andExpect(redirectedUrl("/login?locked"));
        // 本人（10.0.0.7）はログインできる
        mvc.perform(loginFrom("gina-lock", "Correct-pass1", "10.0.0.7"))
                .andExpect(redirectedUrl("/recipes"));
    }

    @Test
    void 同じ場所から多くのユーザー名で失敗するとその場所を止める() throws Exception {
        createUser("hank-ok", "Correct-pass1");
        for (int i = 0; i < 50; i++) {
            mvc.perform(loginFrom("nobody-" + i, "wrong", "10.0.0.99"))
                    .andExpect(redirectedUrl("/login?error"));
        }
        mvc.perform(loginFrom("hank-ok", "Correct-pass1", "10.0.0.99"))
                .andExpect(redirectedUrl("/login?locked"));
    }

    /** 決まった場所（IP アドレス）からログインを申し込む */
    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder loginFrom(
            String username, String password, String ip) {
        return post("/login").param("username", username).param("password", password)
                .with(csrf()).with(fromIp(ip));
    }

    /** リクエストを送ってきた場所（IP アドレス）を変える */
    private static org.springframework.test.web.servlet.request.RequestPostProcessor fromIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    @Test
    void 失敗が10回未満なら正しいパスワードでログインできる() throws Exception {
        createUser("frank-ok", "Correct-pass1");
        for (int i = 0; i < 3; i++) {
            mvc.perform(formLogin().user("frank-ok").password("wrong-" + i))
                    .andExpect(redirectedUrl("/login?error"));
        }
        mvc.perform(formLogin().user("frank-ok").password("Correct-pass1"))
                .andExpect(redirectedUrl("/recipes"));
    }

    // ===== その他：ヘッダー・AI の欄 =====

    @Test
    void ほかのサイトにページのURLを伝えない設定になっている() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getHeader("Referrer-Policy")).isEqualTo("same-origin"));
    }

    @Test
    void AIを使う設定ならAIで下書きのボタンが出る() throws Exception {
        createUser("gina-ai", "Passw0rd!x");
        mvc.perform(get("/recipes/new").with(user("gina-ai")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("aiDraftButton")));
    }
}
