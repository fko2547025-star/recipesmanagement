package com.example.recipesmanagement.ingredient;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.recipesmanagement.user.AppUser;

public interface IngredientRepository extends JpaRepository<Ingredient, Long> {

    // このユーザーの食材を全件
    List<Ingredient> findByUser(AppUser user, Sort sort);

    // このユーザーの食材をカテゴリで絞り込み
    List<Ingredient> findByUserAndCategory(AppUser user, String category, Sort sort);

    // このユーザーの食材を1件（他人の食材なら見つからない）
    Optional<Ingredient> findByIdAndUser(Long id, AppUser user);
}