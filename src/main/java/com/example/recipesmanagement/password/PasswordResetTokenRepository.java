package com.example.recipesmanagement.password;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.recipesmanagement.user.AppUser;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    // ハッシュでトークンを探す（リンクを開いたとき）
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    // そのユーザーのトークンを全部消す（新しいリンクを送る前に、古いリンクを使えなくする）
    void deleteByUser(AppUser user);
}