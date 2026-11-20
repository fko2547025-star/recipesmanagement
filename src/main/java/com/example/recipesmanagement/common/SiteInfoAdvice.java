package com.example.recipesmanagement.common;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 運営者名と問い合わせ先を、全画面に渡す（フッター・プライバシーポリシー・利用規約で使う）。
 * 値は application.properties の app.operator-name・app.contact。
 */
@ControllerAdvice
public class SiteInfoAdvice {

    private final com.example.recipesmanagement.admin.AnnouncementRepository announcementRepository;

    public SiteInfoAdvice(com.example.recipesmanagement.admin.AnnouncementRepository announcementRepository) {
        this.announcementRepository = announcementRepository;
    }

    /** 表示中のお知らせ（ヘッダーの下に帯で出す）。全画面で ${announcements} として使える */
    @ModelAttribute("announcements")
    public List<com.example.recipesmanagement.admin.Announcement> announcements() {
        return announcementRepository.findByActiveTrueOrderByIdDesc();
    }

    @Value("${app.operator-name:CookBuddy 運営チーム}")
    private String operatorName;

    @Value("${app.contact:【000-0000-0000】}")
    private String contact;

    @ModelAttribute("operatorName")
    public String operatorName() {
        return operatorName;
    }

    @ModelAttribute("contact")
    public String contact() {
        return contact;
    }
}
