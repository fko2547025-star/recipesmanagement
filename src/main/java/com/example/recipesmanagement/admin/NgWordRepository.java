package com.example.recipesmanagement.admin;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NgWordRepository extends JpaRepository<NgWord, Long> {

    List<NgWord> findAllByOrderByWordAsc();

    boolean existsByWord(String word);
}
