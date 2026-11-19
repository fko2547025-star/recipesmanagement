package com.example.recipesmanagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling   // 決まった時刻に動く処理（データベースの控え）を有効にする
public class RecipesManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(RecipesManagementApplication.class, args);
    }
}