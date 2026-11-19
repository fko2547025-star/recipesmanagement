package com.example.recipesmanagement.ai;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 全画面に ${aiEnabled}（AI 機能を使う設定か）を渡す。
 * false のときは、レシピ詳細・投稿フォーム・作れるレシピ・おまかせ献立の AI の欄を出さない。
 */
@ControllerAdvice
public class AiModelAdvice {

    private final AiService aiService;

    public AiModelAdvice(AiService aiService) {
        this.aiService = aiService;
    }

    @ModelAttribute("aiEnabled")
    public boolean aiEnabled() {
        return aiService.isEnabled();
    }
}
