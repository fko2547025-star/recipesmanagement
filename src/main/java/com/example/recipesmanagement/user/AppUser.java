package com.example.recipesmanagement.user;

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


}