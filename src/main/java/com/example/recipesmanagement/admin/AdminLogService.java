package com.example.recipesmanagement.admin;

import java.security.Principal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.recipesmanagement.user.AppUserRepository;

/**
 * 管理者の操作を記録する。削除・利用停止・お知らせの作成など、管理者がしたことはすべてここを通して残す。
 */
@Service
public class AdminLogService {

    private final AdminLogRepository logRepository;
    private final AppUserRepository userRepository;

    public AdminLogService(AdminLogRepository logRepository, AppUserRepository userRepository) {
        this.logRepository = logRepository;
        this.userRepository = userRepository;
    }

    /**
     * 記録する。
     *
     * @param admin  操作した管理者（ログイン中の人）
     * @param action 何をしたか（例：「レシピを削除」）
     * @param target 何に対して（例：「カレー」(id=3)）
     * @param detail 理由など（無ければ null）
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void record(Principal admin, String action, String target, String detail) {
        Long adminId = userRepository.findByUsername(admin.getName()).map(u -> u.getId()).orElse(null);
        logRepository.save(new AdminLog(adminId, admin.getName(), action, cut(target, 200), cut(detail, 500)));
    }

    /** 長すぎる文字を切る（テーブルの文字数に収める） */
    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
