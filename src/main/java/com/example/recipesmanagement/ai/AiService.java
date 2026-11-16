package com.example.recipesmanagement.ai;

import java.time.Duration;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;

/**
 * AI（Claude API）を呼び出す共通の部品。
 *
 * APIキーは application.properties の app.ai.api-key（中身は ai-secret.properties に書く）か、
 * 環境変数 ANTHROPIC_API_KEY から読む。どちらも無いときは isEnabled() が false になり、
 * 画面では「AI機能は未設定です」と表示する（アプリのほかの機能はそのまま動く）。
 */
@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private final String model;

    // APIキーが無いときは null（AI機能は使えない）
    private final AnthropicClient client;

    public AiService(@Value("${app.ai.api-key:}") String apiKey,
                     @Value("${app.ai.model:claude-opus-5-5}") String model) {
        this.model = model;

        // 設定ファイルが空なら、環境変数を使う
        String key = (apiKey == null || apiKey.isBlank()) ? System.getenv("ANTHROPIC_API_KEY") : apiKey;

        if (key == null || key.isBlank()) {
            this.client = null;
            log.info("AI機能は未設定です（APIキーがありません）");
        } else {
            this.client = AnthropicOkHttpClient.builder()
                    .apiKey(key.trim())
                    .timeout(Duration.ofSeconds(90))   // 画面で待たせすぎないように
                    .build();
        }
    }

    /** APIキーが設定されていて、AI機能が使えるか */
    public boolean isEnabled() {
        return client != null;
    }

    /**
     * AI に1回質問して、回答の文章を返す。
     *
     * @param system      AI の役割や守ってほしいこと
     * @param userMessage 質問の本文（レシピなどの情報も含めてよい）
     * @throws AiException 未設定・通信失敗など、回答が得られなかったとき（メッセージは画面にそのまま出せる日本語）
     */
    public String ask(String system, String userMessage) {
        if (!isEnabled()) {
            throw new AiException("AI機能は未設定です。管理者にAPIキーの設定を依頼してください。");
        }

        MessageCreateParams params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(16000L)
                .system(system)
                .addUserMessage(userMessage)
                // effort：考える深さ。料理の質問なら LOW で十分速く・安く答えられる
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
                .build();

        try {
            Message response = client.messages().create(params);

            // 安全上の理由で AI が答えなかったとき
            if (response.stopReason().filter(StopReason.REFUSAL::equals).isPresent()) {
                throw new AiException("この質問には答えられませんでした。聞き方を変えてみてください。");
            }

            // 回答は複数のブロックに分かれていることがあるので、文章のブロックだけつなげる
            String text = response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(t -> t.text())
                    .collect(Collectors.joining("\n"))
                    .trim();

            if (text.isEmpty()) {
                throw new AiException("AIから回答が得られませんでした。もう一度お試しください。");
            }
            return text;

        } catch (UnauthorizedException e) {
            log.warn("AI: APIキーが正しくありません", e);
            throw new AiException("AIのAPIキーが正しくありません。設定を確認してください。");
        } catch (RateLimitException e) {
            log.warn("AI: 利用回数の上限に達しました", e);
            throw new AiException("AIが混み合っています。少し待ってからもう一度お試しください。");
        } catch (AnthropicServiceException e) {
            log.warn("AI: APIエラー status={}", e.statusCode(), e);
            throw new AiException("AIでエラーが起きました。時間をおいてもう一度お試しください。");
        } catch (AnthropicIoException e) {
            log.warn("AI: 通信に失敗しました", e);
            throw new AiException("AIに接続できませんでした。ネットワークを確認してください。");
        }
    }

    /** 回答が得られなかったときの例外。メッセージは利用者向けの日本語 */
    public static class AiException extends RuntimeException {
        public AiException(String message) {
            super(message);
        }
    }
}
