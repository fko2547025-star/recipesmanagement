package com.example.recipesmanagement.password;

import java.time.LocalDateTime;

import com.example.recipesmanagement.user.AppUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * パスワード再設定のトークン（password_reset_tokens テーブルの1行）。
 * トークンそのものは保存せず、ハッシュだけを保存する。
 */
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // パスワードを再設定するユーザー
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    // トークンの SHA-256 ハッシュ（16進数64文字）
    @Column(nullable = false, length = 64)
    private String tokenHash;

    // 有効期限（これを過ぎたら使えない）
    @Column(nullable = false)
    private LocalDateTime expiresAt;

    // 1回使ったら true（同じリンクで2回変えられないようにする）
    private boolean used;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected PasswordResetToken() {
    }

    public PasswordResetToken(AppUser user, String tokenHash, LocalDateTime expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /** まだ使えるか（未使用で、期限が切れていない） */
    public boolean isUsable() {
        return !used && expiresAt.isAfter(LocalDateTime.now());
    }

    public Long getId() { return id; }
    public AppUser getUser() { return user; }
    public String getTokenHash() { return tokenHash; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public boolean isUsed() { return used; }
    public void setUsed(boolean used) { this.used = used; }
}