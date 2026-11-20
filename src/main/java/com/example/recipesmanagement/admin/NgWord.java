package com.example.recipesmanagement.admin;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * NGワード（ng_words テーブルの1行）。含まれるレシピ・コメント・ニックネームは投稿できない。
 */
@Entity
@Table(name = "ng_words")
public class NgWord {

    public static final int MAX_LENGTH = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String word;

    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected NgWord() {
    }

    public NgWord(String word) {
        this.word = word;
    }

    public Long getId() { return id; }
    public String getWord() { return word; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
