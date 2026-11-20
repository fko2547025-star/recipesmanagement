package com.example.recipesmanagement.contact;

import java.time.LocalDateTime;
import java.util.Map;

import com.example.recipesmanagement.user.AppUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * お問い合わせ（inquiries テーブルの1行）。
 */
@Entity
@Table(name = "inquiries")
public class Inquiry {

    public static final int MAX_MESSAGE = 1000;
    public static final int MAX_REPLY = 2000;

    // 種類（コード → 画面に出す名前）。並び順もこの順
    public static final Map<String, String> CATEGORIES = new java.util.LinkedHashMap<>();
    static {
        CATEGORIES.put("USAGE", "使い方の質問");
        CATEGORIES.put("BUG", "不具合の報告");
        CATEGORIES.put("ACCOUNT", "アカウントについて");
        CATEGORIES.put("OTHER", "その他");
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 送った人（ログインしていなかったときは null）
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    private String category;
    private String email;
    private String message;
    private String status = "OPEN";     // OPEN（未対応）か DONE（対応済み）
    private String reply;
    private LocalDateTime repliedAt;

    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }

    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    /** 種類の名前（画面用） */
    public String getCategoryLabel() { return CATEGORIES.getOrDefault(category, category); }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isDone() { return "DONE".equals(status); }

    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }

    public LocalDateTime getRepliedAt() { return repliedAt; }
    public void setRepliedAt(LocalDateTime repliedAt) { this.repliedAt = repliedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
