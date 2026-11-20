package com.example.recipesmanagement.admin;

import java.security.Principal;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.recipesmanagement.contact.Inquiry;
import com.example.recipesmanagement.contact.InquiryService;

/**
 * 管理者画面：お知らせ・NGワード・お問い合わせ。
 *
 * | URL                                  | メソッド | 処理                       |
 * |--------------------------------------|----------|----------------------------|
 * | /admin/announcements                 | GET/POST | お知らせの一覧・追加       |
 * | /admin/announcements/{id}/toggle     | POST     | 表示する・しないの切り替え |
 * | /admin/announcements/{id}/delete     | POST     | 削除                       |
 * | /admin/ng-words                      | GET/POST | NGワードの一覧・追加       |
 * | /admin/ng-words/{id}/delete          | POST     | 削除                       |
 * | /admin/inquiries                     | GET      | お問い合わせ（?done で対応済み）|
 * | /admin/inquiries/{id}                | GET      | 詳細と返信の入力           |
 * | /admin/inquiries/{id}/reply          | POST     | メールで返信して対応済みに |
 * | /admin/inquiries/{id}/toggle         | POST     | 対応済み・未対応の切り替え |
 */
@Controller
@RequestMapping("/admin")
public class AdminSiteController {

    private final SiteSettingsService settings;
    private final InquiryService inquiryService;
    private final AdminLogService logService;

    public AdminSiteController(SiteSettingsService settings, InquiryService inquiryService, AdminLogService logService) {
        this.settings = settings;
        this.inquiryService = inquiryService;
        this.logService = logService;
    }

    // ===== お知らせ =====

    @GetMapping("/announcements")
    public String announcements(Model model) {
        model.addAttribute("announcements", settings.announcements());
        model.addAttribute("maxLength", Announcement.MAX_LENGTH);
        return "admin/announcements";
    }

    @PostMapping("/announcements")
    public String addAnnouncement(@RequestParam(defaultValue = "") String message, Principal principal, RedirectAttributes ra) {
        String error = settings.validateAnnouncement(message);
        if (error != null) {
            ra.addFlashAttribute("error", error);
            ra.addFlashAttribute("draft", message);
        } else {
            settings.addAnnouncement(message);
            logService.record(principal, "お知らせを作成", "「" + message.trim() + "」", null);
            ra.addFlashAttribute("message", "お知らせを作成しました（全画面の上に表示されます）");
        }
        return "redirect:/admin/announcements";
    }

    @PostMapping("/announcements/{id:\\d+}/toggle")
    public String toggleAnnouncement(@PathVariable Long id, Principal principal, RedirectAttributes ra) {
        String result = settings.toggleAnnouncement(id);
        logService.record(principal, "お知らせの表示を変更", result, null);
        ra.addFlashAttribute("message", "お知らせの表示を変えました");
        return "redirect:/admin/announcements";
    }

    @PostMapping("/announcements/{id:\\d+}/delete")
    public String deleteAnnouncement(@PathVariable Long id, Principal principal, RedirectAttributes ra) {
        String target = settings.deleteAnnouncement(id);
        logService.record(principal, "お知らせを削除", target, null);
        ra.addFlashAttribute("message", "お知らせを削除しました");
        return "redirect:/admin/announcements";
    }

    // ===== NGワード =====

    @GetMapping("/ng-words")
    public String ngWords(Model model) {
        model.addAttribute("words", settings.ngWords());
        model.addAttribute("maxLength", NgWord.MAX_LENGTH);
        return "admin/ng-words";
    }

    @PostMapping("/ng-words")
    public String addNgWord(@RequestParam(defaultValue = "") String word, Principal principal, RedirectAttributes ra) {
        String error = settings.addNgWord(word);
        if (error != null) {
            ra.addFlashAttribute("error", error);
        } else {
            logService.record(principal, "NGワードを追加", "「" + word.trim() + "」", null);
            ra.addFlashAttribute("message", "「" + word.trim() + "」を追加しました");
        }
        return "redirect:/admin/ng-words";
    }

    @PostMapping("/ng-words/{id:\\d+}/delete")
    public String deleteNgWord(@PathVariable Long id, Principal principal, RedirectAttributes ra) {
        String target = settings.deleteNgWord(id);
        logService.record(principal, "NGワードを削除", target, null);
        ra.addFlashAttribute("message", target + "を削除しました");
        return "redirect:/admin/ng-words";
    }

    // ===== お問い合わせ =====

    @GetMapping("/inquiries")
    public String inquiries(@RequestParam(defaultValue = "false") boolean done, Model model) {
        model.addAttribute("inquiries", inquiryService.list(done));
        model.addAttribute("done", done);
        return "admin/inquiries";
    }

    @GetMapping("/inquiries/{id:\\d+}")
    public String inquiry(@PathVariable Long id, Model model) {
        model.addAttribute("inquiry", inquiryService.find(id));
        model.addAttribute("maxReply", Inquiry.MAX_REPLY);
        return "admin/inquiry";
    }

    @PostMapping("/inquiries/{id:\\d+}/reply")
    public String reply(@PathVariable Long id, @RequestParam(defaultValue = "") String reply,
                        Principal principal, RedirectAttributes ra) {
        String error = inquiryService.reply(id, reply);
        if (error != null) {
            ra.addFlashAttribute("error", error);
            ra.addFlashAttribute("draft", reply);
            return "redirect:/admin/inquiries/" + id;
        }
        logService.record(principal, "お問い合わせに返信", "お問い合わせ(id=" + id + ")", null);
        ra.addFlashAttribute("message", "返信のメールを送り、対応済みにしました");
        return "redirect:/admin/inquiries";
    }

    @PostMapping("/inquiries/{id:\\d+}/toggle")
    public String toggleInquiry(@PathVariable Long id, Principal principal, RedirectAttributes ra) {
        boolean done = inquiryService.toggleDone(id);
        logService.record(principal, done ? "お問い合わせを対応済みに" : "お問い合わせを未対応に戻す",
                "お問い合わせ(id=" + id + ")", null);
        ra.addFlashAttribute("message", done ? "対応済みにしました" : "未対応に戻しました");
        return "redirect:/admin/inquiries/" + id;
    }
}
