package com.example.recipesmanagement.user;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;    // ユーザー名
    private String password;    // パスワード（暗号化済み）
    private String nickname;
    private String avatarPath;
    private String email;
    private String avoidFoods;  // アレルギー・苦手な食材（AI に伝える。任意）

    // 役割：USER（ふつうのユーザー）か ADMIN（管理者：通報の一覧を見て対応できる）。
    // 管理者にするには、データベースで直接 UPDATE users SET role = 'ADMIN' WHERE username = '…'; を実行する
    private String role = "USER";

    // 利用停止中か。利用停止中のユーザーは、ログインしてもすぐにログアウトされ、理由が表示される
    private boolean suspended;
    private String suspendedReason;

    // メールアドレスの確認が済んでいるか。確認が済むまで、パスワード再設定のメールは送らない。
    // 前からいるユーザーは確認済みとして扱う（データベースの既定値も TRUE）
    private boolean emailVerified = true;
    private String emailVerifyToken;           // 確認メールのリンクのトークンのハッシュ（確認が済んだら null）
    private LocalDateTime emailVerifyExpires;  // そのリンクの有効期限

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getAvatarPath() { return avatarPath; }
    public void setAvatarPath(String avatarPath) { this.avatarPath = avatarPath; }

    /**
     * 画面に表示する名前。ニックネームがあればニックネーム、無ければユーザー名。
     * 画面では ${user.displayName} で呼び出せる。
     */
    public String getDisplayName() {
        return (nickname != null && !nickname.isBlank()) ? nickname : username;
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getAvoidFoods() { return avoidFoods; }
    public void setAvoidFoods(String avoidFoods) { this.avoidFoods = avoidFoods; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    /** 管理者か（画面では ${loginUser.admin}） */
    public boolean isAdmin() { return "ADMIN".equals(role); }

    public boolean isSuspended() { return suspended; }
    public void setSuspended(boolean suspended) { this.suspended = suspended; }

    public String getSuspendedReason() { return suspendedReason; }
    public void setSuspendedReason(String suspendedReason) { this.suspendedReason = suspendedReason; }

    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }

    public String getEmailVerifyToken() { return emailVerifyToken; }
    public void setEmailVerifyToken(String emailVerifyToken) { this.emailVerifyToken = emailVerifyToken; }

    public LocalDateTime getEmailVerifyExpires() { return emailVerifyExpires; }
    public void setEmailVerifyExpires(LocalDateTime emailVerifyExpires) { this.emailVerifyExpires = emailVerifyExpires; }

    /** メールアドレスを登録していて、まだ確認が済んでいないか（マイページにお知らせを出す） */
    public boolean isEmailUnverified() {
        return email != null && !emailVerified;
    }
}
