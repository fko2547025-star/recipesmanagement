package com.example.recipesmanagement.ingredient;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngredientRepository extends JpaRepository<Ingredient, Long> {

    // カテゴリで絞り込み
    List<Ingredient> findByCategory(String category, Sort sort);
}