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
     */
    @Transactional
    public void toggle(Long recipeId, AppUser user) {
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        // ifPresentOrElse：見つかったら1つ目の処理、見つからなければ2つ目の処理
        likeRepository.findByUserAndRecipe(user, recipe).ifPresentOrElse(
                // いいね済み → 取り消し
                like -> likeRepository.delete(like),
                // まだ → いいね
                () -> {
                    RecipeLike like = new RecipeLike();
                    like.setUser(user);
                    like.setRecipe(recipe);
                    likeRepository.save(like);
                });
    }
}