package com.example.recipesmanagement.admin;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminLogRepository extends JpaRepository<AdminLog, Long> {

    // 新しい順に200件（画面に出す分）
    List<AdminLog> findTop200ByOrderByIdDesc();
}
