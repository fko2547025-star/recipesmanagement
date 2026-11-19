package com.example.recipesmanagement.report;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.recipesmanagement.comment.Comment;
import com.example.recipesmanagement.comment.CommentRepository;
import com.example.recipesmanagement.common.ImageStorageService;
import com.example.recipesmanagement.recipe.Recipe;
import com.example.recipesmanagement.recipe.RecipeRepository;
import com.example.recipesmanagement.user.AppUser;

/**
 * 通報の受け付けと、管理者の対応（通報されたものを消す・通報を取り下げる）。
 */
@Service
public class ReportService {

    private final ReportRepository reportRepository;
    private final RecipeRepository recipeRepository;
    private final CommentRepository commentRepository;
    private final ImageStorageService imageStorageService;
    private final JdbcTemplate jdbc;

    public ReportService(ReportRepository reportRepository, RecipeRepository recipeRepository,
                         CommentRepository commentRepository, ImageStorageService imageStorageService,
                         JdbcTemplate jdbc) {
        this.reportRepository = reportRepository;
        this.recipeRepository = recipeRepository;
        this.commentRepository = commentRepository;
        this.imageStorageService = imageStorageService;
        this.jdbc = jdbc;
    }

    /** 理由のチェック。問題があればエラーメッセージ、無ければ null */
    public String validate(String reason) {
        if (reason == null || reason.isBlank()) {
            return "通報の理由を入力してください";
        }
        if (reason.trim().length() > Report.MAX_REASON) {
            return "通報の理由は" + Report.MAX_REASON + "文字以内で入力してください";
        }
        return null;
    }

    /** レシピを通報する。レシピが無ければ 404 */
    @Transactional
    public void reportRecipe(Long recipeId, String reason, AppUser reporter) {
        Report report = new Report();
        report.setRecipe(recipeRepository.findById(recipeId).orElseThrow(ReportService::notFound));
        report.setReason(reason.trim());
        report.setReporter(reporter);
        reportRepository.save(report);
    }

    /** コメントを通報する。コメントが無ければ 404。通報されたコメントのレシピの ID を返す */
    @Transactional
    public Long reportComment(Long commentId, String reason, AppUser reporter) {
        Comment comment = commentRepository.findById(commentId).orElseThrow(ReportService::notFound);
        Report report = new Report();
        report.setComment(comment);
        report.setReason(reason.trim());
        report.setReporter(reporter);
        reportRepository.save(report);
        return comment.getRecipe().getId();
    }

    @Transactional(readOnly = true)
    public List<Report> findAll() {
        return reportRepository.findAllByOrderByIdDesc();
    }

    /** 通報を取り下げる（問題が無かったとき） */
    @Transactional
    public void dismiss(Long reportId) {
        reportRepository.deleteById(reportId);
    }

    /**
     * 通報されたレシピ・コメントを消す（そこへの通報も、ON DELETE CASCADE で一緒に消える）。
     *
     * @return 消したデータが使っていた写真のファイル名（呼び出す側で、データベースを消し終わってから消す）
     */
    @Transactional
    public List<String> deleteTarget(Long reportId) {
        Report report = reportRepository.findById(reportId).orElseThrow(ReportService::notFound);
        List<String> images = new ArrayList<>();
        if (report.getRecipe() != null) {
            Recipe recipe = report.getRecipe();
            if (recipe.getImagePath() != null) {
                images.add(recipe.getImagePath());
            }
            // レシピについたコメントの写真も（コメントは ON DELETE CASCADE で一緒に消える）
            images.addAll(jdbc.queryForList(
                    "SELECT image_path FROM comments WHERE recipe_id = ? AND image_path IS NOT NULL",
                    String.class, recipe.getId()));
            recipeRepository.delete(recipe);
        } else if (report.getComment() != null) {
            Comment comment = report.getComment();
            if (comment.getImagePath() != null) {
                images.add(comment.getImagePath());
            }
            commentRepository.delete(comment);
        }
        return images;
    }

    /** 見つからないとき（404 にする） */
    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
