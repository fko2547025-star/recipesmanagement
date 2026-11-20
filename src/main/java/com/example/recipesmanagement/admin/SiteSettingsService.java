package com.example.recipesmanagement.admin;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * お知らせと NGワードの管理（管理者画面）。
 */
@Service
public class SiteSettingsService {

    private final AnnouncementRepository announcementRepository;
    private final NgWordRepository ngWordRepository;

    public SiteSettingsService(AnnouncementRepository announcementRepository, NgWordRepository ngWordRepository) {
        this.announcementRepository = announcementRepository;
        this.ngWordRepository = ngWordRepository;
    }

    // ===== お知らせ =====

    @Transactional(readOnly = true)
    public List<Announcement> announcements() {
        return announcementRepository.findAllByOrderByIdDesc();
    }

    /** 入力チェック。問題があればエラーメッセージ、無ければ null */
    public String validateAnnouncement(String message) {
        if (message == null || message.isBlank()) {
            return "お知らせの文を入力してください";
        }
        if (message.trim().length() > Announcement.MAX_LENGTH) {
            return "お知らせは" + Announcement.MAX_LENGTH + "文字以内で入力してください";
        }
        return null;
    }

    @Transactional
    public void addAnnouncement(String message) {
        Announcement a = new Announcement();
        a.setMessage(message.trim());
        announcementRepository.save(a);
    }

    /** 表示する・しないを切り替える。お知らせの文を返す（記録用） */
    @Transactional
    public String toggleAnnouncement(Long id) {
        Announcement a = announcementRepository.findById(id).orElseThrow(SiteSettingsService::notFound);
        a.setActive(!a.isActive());
        return "「" + a.getMessage() + "」" + (a.isActive() ? " → 表示する" : " → 表示しない");
    }

    /** 削除する。お知らせの文を返す（記録用） */
    @Transactional
    public String deleteAnnouncement(Long id) {
        Announcement a = announcementRepository.findById(id).orElseThrow(SiteSettingsService::notFound);
        announcementRepository.delete(a);
        return "「" + a.getMessage() + "」";
    }

    // ===== NGワード =====

    @Transactional(readOnly = true)
    public List<NgWord> ngWords() {
        return ngWordRepository.findAllByOrderByWordAsc();
    }

    /** 追加する。問題があればエラーメッセージ、追加できたら null */
    @Transactional
    public String addNgWord(String word) {
        String w = word == null ? "" : word.trim();
        if (w.isEmpty()) {
            return "NGワードを入力してください";
        }
        if (w.length() > NgWord.MAX_LENGTH) {
            return "NGワードは" + NgWord.MAX_LENGTH + "文字以内で入力してください";
        }
        if (ngWordRepository.existsByWord(w)) {
            return "「" + w + "」はすでに登録されています";
        }
        ngWordRepository.save(new NgWord(w));
        return null;
    }

    /** 削除する。言葉を返す（記録用） */
    @Transactional
    public String deleteNgWord(Long id) {
        NgWord w = ngWordRepository.findById(id).orElseThrow(SiteSettingsService::notFound);
        ngWordRepository.delete(w);
        return "「" + w.getWord() + "」";
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
