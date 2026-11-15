package com.example.recipesmanagement.follow;

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
 * フォローを表す Entity（follows テーブルの1行）。
 * 「follower が followee をフォローしている」という意味。
 */
@Entity
@Table(name = "follows")
public class Follow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // フォローした人（自分）
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "follower_id", nullable = false)
    private AppUser follower;

    // フォローされた人（相手）
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "followee_id", nullable = false)
    private AppUser followee;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    // JPA が使う、引数なしのコンストラクタ（自分では使わないので protected）
    protected Follow() {
    }

    public Follow(AppUser follower, AppUser followee) {
        this.follower = follower;
        this.followee = followee;
    }

    // @PrePersist：INSERT の直前に自動で呼ばれる。フォローした日時を入れる
    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public AppUser getFollower() { return follower; }
    public AppUser getFollowee() { return followee; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}