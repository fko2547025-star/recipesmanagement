package com.example.recipesmanagement.admin;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    List<Announcement> findAllByOrderByIdDesc();

    // 表示中のお知らせ（新しい順）
    List<Announcement> findByActiveTrueOrderByIdDesc();
}
