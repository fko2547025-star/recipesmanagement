package com.example.recipesmanagement.like;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.recipesmanagement.recipe.Recipe;
import com.example.recipesmanagement.recipe.RecipeRepository;
import com.example.recipesmanagement.user.AppUser;

/**
 * いいねの処理をまとめたクラス。
 */
@Service
public class LikeService {

    private final LikeRepository likeRepository;
    private final RecipeRepository recipeRepository;

    public LikeService(LikeRepository likeRepository, RecipeRepository recipeRepository) {
        this.likeRepository = likeRepository;
        this.recipeRepository = recipeRepository;
    }

    /**
     * いいねを切り替える（トグル）。
     * まだしていなければ「いいね」、すでにしていれば「取り消し」。
     *
     * @return 切り替えたあと、いいねしている状態なら true
     */
    @Transactional
    public boolean toggle(Long recipeId, AppUser user) {
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        // いいね済み → 取り消し
        var existing = likeRepository.findByUserAndRecipe(user, recipe);
        if (existing.isPresent()) {
            likeRepository.delete(existing.get());
            return false;
        }
        // まだ → いいね
        RecipeLike like = new RecipeLike();
        like.setUser(user);
        like.setRecipe(recipe);
        likeRepository.save(like);
        return true;
    }

    /** レシピのいいね数 */
    @Transactional(readOnly = true)
    public long count(Long recipeId) {
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return likeRepository.countByRecipe(recipe);
    }
}