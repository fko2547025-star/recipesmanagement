package com.example.recipesmanagement.ai;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * AI を呼び出す共通の部品。
 *
 * パソコンで動かす Ollama（無料の AI 実行ソフト）に質問を送り、回答を受け取る。
 * インターネット上のサービスは使わないので、料金はかからない。
 *
 * 使う前にパソコンで次の準備が必要：
 *   1. Ollama をインストールして起動しておく（https://ollama.com）
 *   2. ollama pull qwen2.5:7b でモデルを取り込んでおく
 *
 * 場所とモデルは application.properties の app.ai.ollama-url / app.ai.model で変えられる。
 */
@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private final String ollamaUrl;
    private final String model;
    private final RestClient restClient;

    // 少しずつ届く回答（1行に1つの JSON）を読むための部品
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public AiService(@Value("${app.ai.ollama-url:http://localhost:11434}") String ollamaUrl,
                     @Value("${app.ai.model:qwen2.5:7b}") String model) {
        this.ollamaUrl = ollamaUrl;
        this.model = model;

        // パソコンの中で動かす AI は回答に時間がかかりやすいので、待つ時間を長めにする
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)   // Ollama とは普通の HTTP/1.1 でやり取りする
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMinutes(2));

        this.restClient = RestClient.builder()
                .baseUrl(ollamaUrl)
                .requestFactory(factory)
                .build();
    }

    /**
     * AI に1回質問して、回答の文章を返す。
     *
     * @param system      AI の役割や守ってほしいこと
     * @param userMessage 質問の本文（レシピなどの情報も含めてよい）
     * @throws AiException 回答が得られなかったとき（メッセージは画面にそのまま出せる日本語）
     */
    public String ask(String system, String userMessage) {
        // Ollama の /api/chat に送る内容
        // stream=false：回答を少しずつではなく、最後にまとめて受け取る
        Map<String, Object> body = Map.of(
                "model", model,
                "stream", false,
                "messages", withSystem(system, List.of(userMessage(userMessage))));

        try {
            // 返ってくる形：{"message": {"role": "assistant", "content": "回答"}, ...}
            Map<?, ?> response = restClient.post()
                    .uri("/api/chat")
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            String text = extractContent(response);
            if (text.isEmpty()) {
                throw new AiException("AIから回答が得られませんでした。もう一度お試しください。");
            }
            return text;

        } catch (HttpClientErrorException.NotFound e) {
            // モデルが取り込まれていないと 404 が返る
            throw modelNotFound();
        } catch (ResourceAccessException e) {
            // つながらない（Ollama が起動していない）か、時間切れ
            throw connectionProblem(e);
        } catch (RestClientException e) {
            log.warn("AI: Ollama でエラーが起きました", e);
            throw new AiException("AIでエラーが起きました。もう一度お試しください。");
        }
    }

    /**
     * AI に質問して、回答を少しずつ受け取る。届いた分から onChunk に渡す（画面に少しずつ表示するため）。
     *
     * @param system   AI の役割や守ってほしいこと
     * @param messages これまでの会話と今回の質問（userMessage / assistantMessage で作る。最後は今回の質問）
     * @param onChunk  回答の続きが届くたびに呼ばれる
     * @throws AiException 回答が得られなかったとき（メッセージは画面にそのまま出せる日本語）
     */
    public void stream(String system, List<Map<String, String>> messages, Consumer<String> onChunk) {
        // stream=true：Ollama は回答を「1行に1つの JSON」で少しずつ返す
        //   {"message":{"content":"みりん"},"done":false}
        //   {"message":{"content":"の代わりに"},"done":false}
        //   ...
        //   {"done":true}
        Map<String, Object> body = Map.of(
                "model", model,
                "stream", true,
                "messages", withSystem(system, messages));

        try {
            restClient.post()
                    .uri("/api/chat")
                    .body(body)
                    .exchange((request, response) -> {
                        if (response.getStatusCode().value() == 404) {
                            throw modelNotFound();
                        }
                        if (response.getStatusCode().isError()) {
                            log.warn("AI: Ollama がエラーを返しました status={}", response.getStatusCode());
                            throw new AiException("AIでエラーが起きました。もう一度お試しください。");
                        }
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                            String line;
                            boolean any = false;
                            while ((line = reader.readLine()) != null) {
                                if (line.isBlank()) {
                                    continue;
                                }
                                Map<?, ?> part = jsonMapper.readValue(line, Map.class);
                                if (part.get("error") != null) {
                                    log.warn("AI: Ollama のエラー {}", part.get("error"));
                                    throw new AiException("AIでエラーが起きました。もう一度お試しください。");
                                }
                                String text = part.get("message") instanceof Map<?, ?> m && m.get("content") != null
                                        ? m.get("content").toString() : "";
                                if (!text.isEmpty()) {
                                    any = true;
                                    onChunk.accept(text);
                                }
                                if (Boolean.TRUE.equals(part.get("done"))) {
                                    break;
                                }
                            }
                            if (!any) {
                                throw new AiException("AIから回答が得られませんでした。もう一度お試しください。");
                            }
                        }
                        return null;
                    });
        } catch (AiException e) {
            throw e;
        } catch (JacksonException e) {
            log.warn("AI: Ollama の回答を読み取れませんでした", e);
            throw new AiException("AIの回答を読み取れませんでした。もう一度お試しください。");
        } catch (ResourceAccessException e) {
            throw connectionProblem(e);
        } catch (RestClientException e) {
            log.warn("AI: Ollama でエラーが起きました", e);
            throw new AiException("AIでエラーが起きました。もう一度お試しください。");
        }
    }

    /** 利用者の発言（会話の1つ分） */
    public static Map<String, String> userMessage(String content) {
        return Map.of("role", "user", "content", content);
    }

    /** AI の発言（会話の1つ分） */
    public static Map<String, String> assistantMessage(String content) {
        return Map.of("role", "assistant", "content", content);
    }

    /** 先頭に AI への指示（system）を付けた会話のリストを作る */
    private static List<Map<String, String>> withSystem(String system, List<Map<String, String>> messages) {
        List<Map<String, String>> all = new ArrayList<>();
        all.add(Map.of("role", "system", "content", system));
        all.addAll(messages);
        return all;
    }

    private AiException modelNotFound() {
        log.warn("AI: モデル {} が見つかりません", model);
        return new AiException("AIのモデル「" + model + "」がありません。"
                + "パソコンで ollama pull " + model + " を実行してください。");
    }

    /** つながらない（Ollama が起動していない）か、時間切れのときのメッセージ */
    private AiException connectionProblem(ResourceAccessException e) {
        if (isTimeout(e)) {
            log.warn("AI: 回答が時間内に返りませんでした", e);
            return new AiException("AIの回答に時間がかかりすぎました。もう一度お試しいただくか、"
                    + "軽いモデル（qwen2.5:3b など）に変えてください。");
        }
        log.warn("AI: Ollama（{}）に接続できません", ollamaUrl, e);
        return new AiException("AI（Ollama）が起動していません。パソコンで Ollama を起動してください。");
    }

    /** 返ってきた内容から、回答の文章（message.content）を取り出す */
    private static String extractContent(Map<?, ?> response) {
        if (response == null || !(response.get("message") instanceof Map<?, ?> message)) {
            return "";
        }
        Object content = message.get("content");
        return content == null ? "" : content.toString().trim();
    }

    /** 例外の原因をたどって、時間切れかどうかを調べる */
    private boolean isTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof java.net.http.HttpTimeoutException
                    || t instanceof java.net.SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    /** 回答が得られなかったときの例外。メッセージは利用者向けの日本語 */
    public static class AiException extends RuntimeException {
        public AiException(String message) {
            super(message);
        }
    }
}
