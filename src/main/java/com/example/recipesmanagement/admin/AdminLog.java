package com.example.recipesmanagement.admin;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 管理者の操作の記録（admin_logs テーブルの1行）。
 * 管理者が退会しても記録は残すので、管理者の名前も文字で保存しておく。
 */
@Entity
@Table(name = "admin_logs")
public class AdminLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long adminId;
    private String adminName;
    private String action;      // 何をしたか（例：レシピを削除）
    private String target;      // 何に対して（例：「カレー」(id=3)）
    private String detail;      // 理由など

    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected AdminLog() {
    }

    public AdminLog(Long adminId, String adminName, String action, String target, String detail) {
        this.adminId = adminId;
        this.adminName = adminName;
        this.action = action;
        this.target = target;
        this.detail = detail;
    }

    public Long getId() { return id; }
    public Long getAdminId() { return adminId; }
    public String getAdminName() { return adminName; }
    public String getAction() { return action; }
    public String getTarget() { return target; }
    public String getDetail() { return detail; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
