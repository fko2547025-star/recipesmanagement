package com.example.recipesmanagement.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * エラーページ：英語の「Whitelabel Error Page」ではなく、CookBuddy の日本語のページが出ること。
 * エラーページは本物のサーバーでしか動かない（MockMvc では出ない）ので、サーバーを起動して確かめる。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ErrorPageTest {

    @LocalServerPort
    int port;

    @MockitoBean
    JavaMailSender mailSender;

    private HttpResponse<String> get(String path) throws Exception {
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).header("Accept", "text/html").build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void ないレシピは日本語の404ページになる() throws Exception {
        HttpResponse<String> res = get("/recipes/999999");
        assertThat(res.statusCode()).isEqualTo(404);
        assertThat(res.body()).contains("ページが見つかりません").contains("CookBuddy").doesNotContain("Whitelabel");
    }
}
