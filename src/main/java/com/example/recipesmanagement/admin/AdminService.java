package com.example.recipesmanagement.admin;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.recipesmanagement.comment.Comment;
import com.example.recipesmanagement.comment.CommentRepository;
import com.example.recipesmanagement.contact.InquiryRepository;
import com.example.recipesmanagement.recipe.Recipe;
import com.example.recipesmanagement.recipe.RecipeRepository;
import com.example.recipesmanagement.user.AccountDeletionService;
import com.example.recipesmanagement.user.AppUser;
import com.example.recipesmanagement.user.AppUserRepository;

/**
 * 管理者画面の処理（数字のまとめ・ユーザー・レシピ・コメント）。
 *
 * 一覧は、件数や投稿者名も一緒に出したいので、SQL（JdbcTemplate）でまとめて取り出す。
 * 一覧に出すのは新しい順に最大 LIST_LIMIT 件まで（それより古いものは検索で探す）。
 */
@Service
public class AdminService {

    public static final int LIST_LIMIT = 100;
    public static final int MAX_REASON = 200;

    private final JdbcTemplate jdbc;
    private final AppUserRepository userRepository;
    private final RecipeRepository recipeRepository;
    private final CommentRepository commentRepository;
    private final InquiryRepository inquiryRepository;
    private final AccountDeletionService accountDeletionService;

    public AdminService(JdbcTemplate jdbc, AppUserRepository userRepository, RecipeRepository recipeRepository,
                        CommentRepository commentRepository, InquiryRepository inquiryRepository,
                        AccountDeletionService accountDeletionService) {
        this.jdbc = jdbc;
        this.userRepository = userRepository;
        this.recipeRepository = recipeRepository;
        this.commentRepository = commentRepository;
        this.inquiryRepository = inquiryRepository;
        this.accountDeletionService = accountDeletionService;
    }

    // ===== ダッシュボード =====

    /** ダッシュボードの数字 */
    public record Stats(long users, long suspendedUsers, long recipes, long recipesToday, long comments,
                        long openReports, long openInquiries) {
    }

    @Transactional(readOnly = true)
    public Stats stats() {
        Timestamp today = Timestamp.valueOf(LocalDateTime.now().toLocalDate().atStartOfDay());
        return new Stats(
                count("SELECT COUNT(*) FROM users WHERE role <> 'ADMIN'"),
                count("SELECT COUNT(*) FROM users WHERE suspended = TRUE"),
                count("SELECT COUNT(*) FROM recipes"),
                jdbc.queryForObject("SELECT COUNT(*) FROM recipes WHERE created_at >= ?", Long.class, today),
                count("SELECT COUNT(*) FROM comments"),
                count("SELECT COUNT(*) FROM reports"),
                inquiryRepository.countByStatus("OPEN"));
    }

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    // ===== ユーザー =====

    /** ユーザーの一覧の1行 */
    public record UserRow(long id, String username, String nickname, String email, boolean emailVerified,
                          boolean admin, boolean suspended, String suspendedReason, long recipeCount,
                          long commentCount, long reportedCount) {
    }

    /** ユーザーの一覧（ユーザーID・ニックネーム・メールアドレスで検索） */
    @Transactional(readOnly = true)
    public List<UserRow> users(String q) {
        String like = like(q);
        return jdbc.query("""
                SELECT u.id, u.username, u.nickname, u.email, u.email_verified, u.role, u.suspended, u.suspended_reason,
                       (SELECT COUNT(*) FROM recipes r WHERE r.user_id = u.id) AS recipe_count,
                       (SELECT COUNT(*) FROM comments c WHERE c.user_id = u.id) AS comment_count,
                       (SELECT COUNT(*) FROM reports rp
                          WHERE rp.recipe_id IN (SELECT id FROM recipes WHERE user_id = u.id)
                             OR rp.comment_id IN (SELECT id FROM comments WHERE user_id = u.id)) AS reported_count
                FROM users u
                WHERE CAST(? AS VARCHAR) IS NULL OR LOWER(u.username) LIKE ? OR LOWER(u.nickname) LIKE ? OR LOWER(u.email) LIKE ?
                ORDER BY u.id DESC
                LIMIT ?""",
                (rs, i) -> new UserRow(rs.getLong("id"), rs.getString("username"), rs.getString("nickname"),
                        rs.getString("email"), rs.getBoolean("email_verified"), "ADMIN".equals(rs.getString("role")),
                        rs.getBoolean("suspended"), rs.getString("suspended_reason"), rs.getLong("recipe_count"),
                        rs.getLong("comment_count"), rs.getLong("reported_count")),
                like, like, like, like, LIST_LIMIT);
    }

    /** 利用停止にする。管理者は止められない。止めたユーザー名を返す */
    @Transactional
    public String suspend(Long userId, String reason) {
        AppUser user = normalUser(userId);
        user.setSuspended(true);
        user.setSuspendedReason(reason == null || reason.isBlank() ? null : reason.trim());
        return user.getUsername();
    }

    /** 利用停止を解除する。解除したユーザー名を返す */
    @Transactional
    public String unsuspend(Long userId) {
        AppUser user = normalUser(userId);
        user.setSuspended(false);
        user.setSuspendedReason(null);
        return user.getUsername();
    }

    /** 強制退会（そのユーザーのデータをすべて消す）。消した写真のファイル名を返す（呼び出す側でファイルを消す） */
    @Transactional
    public List<String> forceDelete(Long userId) {
        normalUser(userId);
        return accountDeletionService.delete(userId);
    }

    @Transactional(readOnly = true)
    public String usernameOf(Long userId) {
        return userRepository.findById(userId).map(AppUser::getUsername).orElse("(id=" + userId + ")");
    }

    /** ふつうのユーザーを取り出す。いなければ 404、管理者なら 400（管理者どうしで止めたり消したりしない） */
    private AppUser normalUser(Long userId) {
        AppUser user = userRepository.findById(userId).orElseThrow(AdminService::notFound);
        if (user.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "管理者は利用停止・退会させられません");
        }
        return user;
    }

    // ===== レシピ =====

    /** レシピの一覧の1行 */
    public record RecipeRow(long id, String title, String category, String author, LocalDateTime createdAt,
                            boolean featured, long likeCount, long commentCount, long reportCount) {
    }

    /** レシピの一覧（料理名・投稿者で検索。featuredOnly ならおすすめだけ） */
    @Transactional(readOnly = true)
    public List<RecipeRow> recipes(String q, boolean featuredOnly) {
        String like = like(q);
        return jdbc.query("""
                SELECT r.id, r.title, r.category, u.username, r.created_at, r.featured,
                       (SELECT COUNT(*) FROM likes l WHERE l.recipe_id = r.id) AS like_count,
                       (SELECT COUNT(*) FROM comments c WHERE c.recipe_id = r.id) AS comment_count,
                       (SELECT COUNT(*) FROM reports rp WHERE rp.recipe_id = r.id) AS report_count
                FROM recipes r JOIN users u ON u.id = r.user_id
                WHERE (CAST(? AS VARCHAR) IS NULL OR LOWER(r.title) LIKE ? OR LOWER(u.username) LIKE ?)
                  AND (? = FALSE OR r.featured = TRUE)
                ORDER BY r.id DESC
                LIMIT ?""",
                (rs, i) -> new RecipeRow(rs.getLong("id"), rs.getString("title"), rs.getString("category"),
                        rs.getString("username"), toLocal(rs.getTimestamp("created_at")), rs.getBoolean("featured"),
                        rs.getLong("like_count"), rs.getLong("comment_count"), rs.getLong("report_count")),
                like, like, like, featuredOnly, LIST_LIMIT);
    }

    /** おすすめにする・外す（切り替え）。「料理名」(id=…) を返す */
    @Transactional
    public String toggleFeatured(Long recipeId) {
        Recipe recipe = recipeRepository.findById(recipeId).orElseThrow(AdminService::notFound);
        recipe.setFeatured(!recipe.isFeatured());
        return describe(recipe) + (recipe.isFeatured() ? " → おすすめにした" : " → おすすめから外した");
    }

    @Transactional(readOnly = true)
    public String describeRecipe(Long recipeId) {
        return recipeRepository.findById(recipeId).map(AdminService::describe).orElseThrow(AdminService::notFound);
    }

    /**
     * レシピを消す（いいね・コメント・通報は、データベースの ON DELETE CASCADE で一緒に消える）。
     * 消した写真のファイル名を返す（レシピの写真＋コメントの写真）。
     */
    @Transactional
    public List<String> deleteRecipe(Long recipeId) {
        Recipe recipe = recipeRepository.findById(recipeId).orElseThrow(AdminService::notFound);
        List<String> images = new ArrayList<>();
        if (recipe.getImagePath() != null) {
            images.add(recipe.getImagePath());
        }
        images.addAll(commentRepository.findImagePathsByRecipe(recipe));
        recipeRepository.delete(recipe);
        return images;
    }

    // ===== コメント =====

    /** コメントの一覧の1行 */
    public record CommentRow(long id, String content, boolean hasImage, boolean cookedReport, String author,
                             long recipeId, String recipeTitle, LocalDateTime createdAt, long reportCount) {
    }

    /** コメントの一覧（内容・投稿者で検索） */
    @Transactional(readOnly = true)
    public List<CommentRow> comments(String q) {
        String like = like(q);
        return jdbc.query("""
                SELECT c.id, c.content, c.image_path, c.cooked_report, u.username, r.id AS recipe_id, r.title, c.created_at,
                       (SELECT COUNT(*) FROM reports rp WHERE rp.comment_id = c.id) AS report_count
                FROM comments c JOIN users u ON u.id = c.user_id JOIN recipes r ON r.id = c.recipe_id
                WHERE CAST(? AS VARCHAR) IS NULL OR LOWER(c.content) LIKE ? OR LOWER(u.username) LIKE ?
                ORDER BY c.id DESC
                LIMIT ?""",
                (rs, i) -> new CommentRow(rs.getLong("id"), rs.getString("content"), rs.getString("image_path") != null,
                        rs.getBoolean("cooked_report"), rs.getString("username"), rs.getLong("recipe_id"),
                        rs.getString("title"), toLocal(rs.getTimestamp("created_at")), rs.getLong("report_count")),
                like, like, like, LIST_LIMIT);
    }

    @Transactional(readOnly = true)
    public String describeComment(Long commentId) {
        Comment c = commentRepository.findWithDetailsById(commentId).orElseThrow(AdminService::notFound);
        String text = c.getContent() == null ? "" : c.getContent();
        return "@" + c.getUser().getUsername() + " のコメント「" + (text.length() > 30 ? text.substring(0, 30) + "…" : text)
                + "」(id=" + c.getId() + ")";
    }

    /** コメントを消す。消した写真のファイル名を返す */
    @Transactional
    public List<String> deleteComment(Long commentId) {
        Comment comment = commentRepository.findById(commentId).orElseThrow(AdminService::notFound);
        List<String> images = comment.getImagePath() == null ? List.of() : List.of(comment.getImagePath());
        commentRepository.delete(comment);
        return images;
    }

    // ===== 内部で使う処理 =====

    private static String describe(Recipe recipe) {
        return "「" + recipe.getTitle() + "」(id=" + recipe.getId() + ")";
    }

    /** 検索語を LIKE 用（%語%・小文字）にする。空なら null（＝しぼりこまない） */
    private static String like(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String escaped = q.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private static LocalDateTime toLocal(Timestamp t) {
        return t == null ? null : t.toLocalDateTime();
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
