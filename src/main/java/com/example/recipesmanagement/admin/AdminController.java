package com.example.recipesmanagement.admin;

import java.security.Principal;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.recipesmanagement.common.ImageStorageService;
import com.example.recipesmanagement.report.ReportService;

/**
 * 管理者画面：ダッシュボード・ユーザー・レシピ・コメント・操作の記録。
 * /admin/** は ADMIN だけが開ける（SecurityConfig）。
 *
 * | URL                              | メソッド | 処理                         |
 * |----------------------------------|----------|------------------------------|
 * | /admin                           | GET      | ダッシュボード               |
 * | /admin/users                     | GET      | ユーザーの一覧（?q= で検索） |
 * | /admin/users/{id}/suspend        | POST     | 利用停止（理由つき）         |
 * | /admin/users/{id}/unsuspend      | POST     | 利用停止の解除               |
 * | /admin/users/{id}/delete         | POST     | 強制退会                     |
 * | /admin/recipes                   | GET      | レシピの一覧（?q= ?featured）|
 * | /admin/recipes/{id}/feature      | POST     | おすすめの切り替え           |
 * | /admin/recipes/{id}/delete       | POST     | レシピの削除                 |
 * | /admin/comments                  | GET      | コメントの一覧（?q=）        |
 * | /admin/comments/{id}/delete      | POST     | コメントの削除               |
 * | /admin/logs                      | GET      | 操作の記録                   |
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final AdminService adminService;
    private final AdminLogService logService;
    private final AdminLogRepository logRepository;
    private final ReportService reportService;
    private final ImageStorageService imageStorageService;

    public AdminController(AdminService adminService, AdminLogService logService, AdminLogRepository logRepository,
                           ReportService reportService, ImageStorageService imageStorageService) {
        this.adminService = adminService;
        this.logService = logService;
        this.logRepository = logRepository;
        this.reportService = reportService;
        this.imageStorageService = imageStorageService;
    }

    // ===== ダッシュボード =====

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("stats", adminService.stats());
        model.addAttribute("recentLogs", logRepository.findTop200ByOrderByIdDesc().stream().limit(5).toList());
        return "admin/dashboard";
    }

    // ===== ユーザー =====

    @GetMapping("/users")
    public String users(@RequestParam(required = false) String q, Model model) {
        model.addAttribute("users", adminService.users(q));
        model.addAttribute("q", q);
        model.addAttribute("limit", AdminService.LIST_LIMIT);
        model.addAttribute("maxReason", AdminService.MAX_REASON);
        return "admin/users";
    }

    @PostMapping("/users/{id:\\d+}/suspend")
    public String suspend(@PathVariable Long id, @RequestParam(defaultValue = "") String reason,
                          Principal principal, RedirectAttributes ra) {
        if (reason.trim().length() > AdminService.MAX_REASON) {
            ra.addFlashAttribute("error", "理由は" + AdminService.MAX_REASON + "文字以内で入力してください");
            return "redirect:/admin/users";
        }
        String name = adminService.suspend(id, reason);
        logService.record(principal, "ユーザーを利用停止", "@" + name, reason);
        ra.addFlashAttribute("message", "@" + name + " を利用停止にしました");
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id:\\d+}/unsuspend")
    public String unsuspend(@PathVariable Long id, Principal principal, RedirectAttributes ra) {
        String name = adminService.unsuspend(id);
        logService.record(principal, "利用停止を解除", "@" + name, null);
        ra.addFlashAttribute("message", "@" + name + " の利用停止を解除しました");
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id:\\d+}/delete")
    public String forceDelete(@PathVariable Long id, Principal principal, RedirectAttributes ra) {
        String name = adminService.usernameOf(id);
        List<String> images = adminService.forceDelete(id);
        images.forEach(imageStorageService::delete);   // データベースを消し終わってから、写真のファイルを消す
        logService.record(principal, "ユーザーを強制退会", "@" + name, null);
        ra.addFlashAttribute("message", "@" + name + " を退会させました");
        return "redirect:/admin/users";
    }

    // ===== レシピ =====

    @GetMapping("/recipes")
    public String recipes(@RequestParam(required = false) String q,
                          @RequestParam(defaultValue = "false") boolean featured, Model model) {
        model.addAttribute("recipes", adminService.recipes(q, featured));
        model.addAttribute("q", q);
        model.addAttribute("featured", featured);
        model.addAttribute("limit", AdminService.LIST_LIMIT);
        return "admin/recipes";
    }

    /**
     * おすすめの切り替え。
     *
     * @param back 終わったあとに戻る画面（レシピの詳細から押したときはその画面。/ で始まる自分のサイトの URL だけ受け付ける）
     */
    @PostMapping("/recipes/{id:\\d+}/feature")
    public String toggleFeatured(@PathVariable Long id, @RequestParam(required = false) String back,
                                 Principal principal, RedirectAttributes ra) {
        String result = adminService.toggleFeatured(id);
        logService.record(principal, "おすすめを変更", result, null);
        ra.addFlashAttribute("message", result);
        return "redirect:" + safeBack(back, "/admin/recipes");
    }

    @PostMapping("/recipes/{id:\\d+}/delete")
    public String deleteRecipe(@PathVariable Long id, Principal principal, RedirectAttributes ra) {
        String target = adminService.describeRecipe(id);
        List<String> images = adminService.deleteRecipe(id);
        images.forEach(imageStorageService::delete);
        logService.record(principal, "レシピを削除", target, null);
        ra.addFlashAttribute("message", target + " を削除しました");
        return "redirect:/admin/recipes";
    }

    // ===== コメント =====

    @GetMapping("/comments")
    public String comments(@RequestParam(required = false) String q, Model model) {
        model.addAttribute("comments", adminService.comments(q));
        model.addAttribute("q", q);
        model.addAttribute("limit", AdminService.LIST_LIMIT);
        return "admin/comments";
    }

    @PostMapping("/comments/{id:\\d+}/delete")
    public String deleteComment(@PathVariable Long id, Principal principal, RedirectAttributes ra) {
        String target = adminService.describeComment(id);
        List<String> images = adminService.deleteComment(id);
        images.forEach(imageStorageService::delete);
        logService.record(principal, "コメントを削除", target, null);
        ra.addFlashAttribute("message", "コメントを削除しました");
        return "redirect:/admin/comments";
    }

    // ===== 通報 =====

    @GetMapping("/reports")
    public String reports(Model model) {
        model.addAttribute("reports", reportService.findAll());
        return "admin/reports";
    }

    @PostMapping("/reports/{id:\\d+}/delete-target")
    public String deleteReported(@PathVariable Long id, Principal principal, RedirectAttributes ra) {
        String target = reportService.describeTarget(id);
        String reason = reportService.reasonOf(id);
        List<String> images = reportService.deleteTarget(id);
        images.forEach(imageStorageService::delete);
        logService.record(principal, "通報されたものを削除", target, "通報の理由：" + reason);
        ra.addFlashAttribute("message", "通報されたものを削除しました");
        return "redirect:/admin/reports";
    }

    @PostMapping("/reports/{id:\\d+}/dismiss")
    public String dismissReport(@PathVariable Long id, Principal principal, RedirectAttributes ra) {
        String target = reportService.describeTarget(id);
        String reason = reportService.reasonOf(id);
        reportService.dismiss(id);
        logService.record(principal, "通報を取り下げ（問題なし）", target, "通報の理由：" + reason);
        ra.addFlashAttribute("message", "通報を取り下げました");
        return "redirect:/admin/reports";
    }

    // ===== 操作の記録 =====

    @GetMapping("/logs")
    public String logs(Model model) {
        model.addAttribute("logs", logRepository.findTop200ByOrderByIdDesc());
        return "admin/logs";
    }

    /** 戻り先は、自分のサイトの中の URL（/ で始まり、// で始まらない）だけにする（ほかのサイトへ飛ばされないように） */
    static String safeBack(String back, String fallback) {
        return back != null && back.startsWith("/") && !back.startsWith("//") && !back.contains("\\") ? back : fallback;
    }
}
