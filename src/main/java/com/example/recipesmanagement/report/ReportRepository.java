package com.example.recipesmanagement.report;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Long> {

    // 管理画面用：新しい順に全部。画面で使う相手（通報した人・レシピ・コメントとその投稿者）も一緒に読む
    @EntityGraph(attributePaths = {"reporter", "recipe", "recipe.user", "comment", "comment.user", "comment.recipe"})
    List<Report> findAllByOrderByIdDesc();
}
