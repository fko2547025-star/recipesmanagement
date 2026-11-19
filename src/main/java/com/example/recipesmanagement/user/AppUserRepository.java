package com.example.recipesmanagement.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    // ユーザー名で1件探す
    Optional<AppUser> findByUsername(String username);

    // そのユーザー名がすでに使われているか
    boolean existsByUsername(String username);

    // メール確認のリンクのトークン（ハッシュ）で1件探す
    Optional<AppUser> findByEmailVerifyToken(String emailVerifyToken);
}