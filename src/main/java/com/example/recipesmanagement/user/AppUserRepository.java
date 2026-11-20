package com.example.recipesmanagement.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    // ユーザー名で1件探す
    Optional<AppUser> findByUsername(String username);

    // そのユーザー名がすでに使われているか
    boolean existsByUsername(String username);

    // 役割（USER・ADMIN）で探す・数える（管理者の一覧、最後の管理者を消さないためのチェック）
    java.util.List<AppUser> findByRoleOrderByIdAsc(String role);

    long countByRole(String role);

    // メール確認のリンクのトークン（ハッシュ）で1件探す
    Optional<AppUser> findByEmailVerifyToken(String emailVerifyToken);
}