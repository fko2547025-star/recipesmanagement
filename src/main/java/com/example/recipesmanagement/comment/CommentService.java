package com.example.recipesmanagement.comment;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.recipesmanagement.recipe.Recipe;
import com.example.recipesmanagement.recipe.RecipeRepository;
import com.example.recipesmanagement.user.AppUser;

/**
 * コメントの投稿・編集・削除の処理をまとめたクラス。
 */
@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final RecipeRepository recipeRepository;

    public CommentService(CommentRepository commentRepository, RecipeRepository recipeRepository) {
        this.commentRepository = commentRepository;
        this.recipeRepository = recipeRepository;
    }

    /**
     * コメントの入力チェック。問題があればエラーメッセージ、無ければ null を返す。
     */
    public String validate(String content) {
        if (content == null || content.isBlank()) {
            return "コメントを入力してください";
        }
        if (content.trim().length() > Comment.MAX_LENGTH) {
            return "コメントは" + Comment.MAX_LENGTH + "文字以内で入力してください";
        }
        return null;
    }

    /**
     * コメントを投稿する。
     */
    @Transactional
    public void create(Long recipeId, String content, AppUser user) {
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        Comment comment = new Comment();
        comment.setRecipe(recipe);
        comment.setUser(user);       // コメントした人はログイン中のユーザー
        comment.setContent(content.trim());
        commentRepository.save(comment);
    }

    /**
     * 自分が書いたコメントを取得する。存在しなければ 404、他人のコメントなら 403。
     */
    @Transactional(readOnly = true)
    public Comment findOwned(Long commentId, AppUser user) {
        Comment comment = commentRepository.findWithDetailsById(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (!comment.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return comment;
    }

    /**
     * コメントを更新する。
     *
     * @return コメントしたレシピの id（更新後に詳細画面へ戻るため）
     */
    @Transactional
    public Long update(Long commentId, String content, AppUser user) {
        Comment comment = findOwned(commentId, user);
        comment.setContent(content.trim());
        comment.setUpdatedAt(LocalDateTime.now());
        // save() 不要：@Transactional の中なので、変更検知で自動的に UPDATE される
        return comment.getRecipe().getId();
    }

    /**
     * コメントを削除する。
     *
     * @return コメントしたレシピの id
     */
    @Transactional
    public Long delete(Long commentId, AppUser user) {
        Comment comment = findOwned(commentId, user);
        Long recipeId = comment.getRecipe().getId();
        commentRepository.delete(comment);
        return recipeId;
    }
}