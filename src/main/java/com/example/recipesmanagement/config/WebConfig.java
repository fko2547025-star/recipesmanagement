package com.example.recipesmanagement.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.example.recipesmanagement.common.ImageStorageService;

/**
 * Web まわりの設定。
 *
 * /uploads/○○.jpg にアクセスされたら、写真の保存先フォルダにある ○○.jpg を返す。
 * コントローラーを書かなくても、ファイルをそのまま返してくれる。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final ImageStorageService imageStorageService;

    public WebConfig(ImageStorageService imageStorageService) {
        this.imageStorageService = imageStorageService;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 保存先フォルダを「file:///C:/Users/.../recipes-uploads/」の形にする
        String location = imageStorageService.getUploadDir().toUri().toString();
        if (!location.endsWith("/")) {
            location += "/";   // フォルダを表すには最後の / が必要
        }

        // /uploads/ から始まる URL → 保存先フォルダのファイル
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location);
    }
}