package com.example.recipesmanagement.recipe;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * ホームの「カテゴリから探す」に出す写真。
 *
 * 写真は src/main/resources/static/images/categories/ に、下の FILE_NAMES の名前で置く（.jpg か .webp）。
 * 写真が置かれていないカテゴリは、今までどおり絵文字を表示する（写真を置けば、コードを変えずに切り替わる）。
 */
@Component
public class CategoryImages {

    /** カテゴリ → 写真のファイル名（拡張子なし） */
    private static final Map<String, String> FILE_NAMES = Map.ofEntries(
            Map.entry("和食", "washoku"),
            Map.entry("洋食", "yoshoku"),
            Map.entry("中華", "chuka"),
            Map.entry("肉", "niku"),
            Map.entry("魚", "sakana"),
            Map.entry("野菜", "yasai"),
            Map.entry("ご飯・麺", "gohan-men"),
            Map.entry("汁物", "shirumono"),
            Map.entry("デザート", "dessert"),
            Map.entry("その他", "sonota"));

    private static final List<String> EXTENSIONS = List.of(".jpg", ".webp");

    /**
     * 写真があるカテゴリだけ「カテゴリ → 写真の URL」を返す（例：和食 → /images/categories/washoku.jpg）。
     * 画面では categoryImages.get(c) が null なら絵文字を出す。
     */
    public Map<String, String> urls() {
        Map<String, String> urls = new LinkedHashMap<>();
        FILE_NAMES.forEach((category, name) -> {
            for (String ext : EXTENSIONS) {
                if (new ClassPathResource("static/images/categories/" + name + ext).exists()) {
                    urls.put(category, "/images/categories/" + name + ext);
                    break;
                }
            }
        });
        return urls;
    }
}
