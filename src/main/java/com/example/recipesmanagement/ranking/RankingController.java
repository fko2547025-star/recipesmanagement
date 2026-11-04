package com.example.recipesmanagement.ranking;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * ランキングを担当するコントローラー。
 *
 * | URL                    | メソッド | 処理               | ログイン |
 * |------------------------|----------|--------------------|----------|
 * | /ranking               | GET      | 累計ランキング     | 不要     |
 * | /ranking?period=week   | GET      | 週間（直近7日）    | 不要     |
 * | /ranking?period=month  | GET      | 月間（直近30日）   | 不要     |
 */
@Controller
public class RankingController {

    // 受け付ける期間（これ以外が来たら累計にする）
    private static final List<String> PERIODS = List.of("all", "week", "month");

    private final RankingService rankingService;

    public RankingController(RankingService rankingService) {
        this.rankingService = rankingService;
    }

    @GetMapping("/ranking")
    public String ranking(@RequestParam(defaultValue = "all") String period, Model model) {
        if (!PERIODS.contains(period)) {
            period = "all";
        }

        model.addAttribute("period", period);   // どのタブを選択中か表示するため
        model.addAttribute("items", rankingService.getRanking(period));
        return "ranking/index";
    }
}