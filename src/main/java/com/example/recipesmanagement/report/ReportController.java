package com.example.recipesmanagement.report;

import java.security.Principal;
import java.time.Duration;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.recipesmanagement.common.ImageStorageService;
import com.example.recipesmanagement.common.RateLimiter;
import com.example.recipesmanagement.user.AppUser;
import com.example.recipesmanagement.user.AppUserRepository;

/**
 * 通報（ログイン必須）と、管理者の通報一覧（ADMIN だけ。SecurityConfig で /admin/** を制限）。
 *
 * | URL                              | メソッド | 処理                         |
 * |----------------------------------|----------|------------------------------|
 * | /reports/new?recipeId= / ?commentId= | GET  | 通報の画面                   |
 * | /reports                         | POST     | 通報する                     |
 * | /admin/reports                   | GET      | 通報の一覧（管理者）         |
 * | /admin/reports/{id}/delete-target| POST     | 通報されたものを消す（管理者）|
 * | /admin/reports/{id}/dismiss      | POST     | 通報を取り下げる（管理者）   |
 */
@Controller
public class ReportController {

    // 1人が1時間に通報できる数（いたずらで大量に通報されないように）
    public static final int REPORTS_PER_HOUR = 10;

    private final ReportService reportService;
    private final AppUserRepository userRepository;
    private final ImageStorageService imageStorageService;
    private final RateLimiter rateLimiter;

    public ReportController(ReportService reportService, AppUserRepository userRepository,
                            ImageStorageService imageStorageService, RateLimiter rateLimiter) {
        this.reportService = reportService;
        this.userRepository = userRepository;
        this.imageStorageService = imageStorageService;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/reports/new")
    public String form(@RequestParam(required = false) Long recipeId,
                       @RequestParam(required = false) Long commentId,
                       Model model) {
        if ((recipeId == null) == (commentId == null)) {
            return "redirect:/recipes";   // どちらか一方だけを指定する
        }
        model.addAttribute("recipeId", recipeId);
        model.addAttribute("commentId", commentId);
        model.addAttribute("maxLength", Report.MAX_REASON);
        return "reports/new";
    }

    @PostMapping("/reports")
    public String create(@RequestParam(required = false) Long recipeId,
                         @RequestParam(required = false) Long commentId,
                         @RequestParam(defaultValue = "") String reason,
                         Principal principal,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if ((recipeId == null) == (commentId == null)) {
            return "redirect:/recipes";
        }
        String error = reportService.validate(reason);
        if (error == null && !rateLimiter.tryAcquire("report", principal.getName(), REPORTS_PER_HOUR, Duration.ofHours(1))) {
            error = "短い時間にたくさん通報されています。しばらくしてからもう一度お試しください";
        }
        if (error != null) {
            model.addAttribute("recipeId", recipeId);
            model.addAttribute("commentId", commentId);
            model.addAttribute("reason", reason);
            model.addAttribute("maxLength", Report.MAX_REASON);
            model.addAttribute("error", error);
            return "reports/new";
        }

        AppUser reporter = userRepository.findByUsername(principal.getName()).orElseThrow();
        Long backTo = recipeId;
        if (recipeId != null) {
            reportService.reportRecipe(recipeId, reason, reporter);
        } else {
            backTo = reportService.reportComment(commentId, reason, reporter);
        }
        redirectAttributes.addFlashAttribute("reported", true);
        return "redirect:/recipes/" + backTo;
    }

    // ===== 管理者 =====

    @GetMapping("/admin/reports")
    public String list(Model model) {
        model.addAttribute("reports", reportService.findAll());
        return "admin/reports";
    }

    @PostMapping("/admin/reports/{id:\\d+}/delete-target")
    public String deleteTarget(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        List<String> images = reportService.deleteTarget(id);
        images.forEach(imageStorageService::delete);   // データベースを消し終わってから、写真のファイルを消す
        redirectAttributes.addFlashAttribute("message", "通報されたものを削除しました");
        return "redirect:/admin/reports";
    }

    @PostMapping("/admin/reports/{id:\\d+}/dismiss")
    public String dismiss(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        reportService.dismiss(id);
        redirectAttributes.addFlashAttribute("message", "通報を取り下げました");
        return "redirect:/admin/reports";
    }
}
