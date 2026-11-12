package com.example.recipesmanagement.ingredient;

import java.time.LocalDate;
import java.text.DecimalFormat;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import com.example.recipesmanagement.user.AppUser;

import org.springframework.format.annotation.DateTimeFormat;


import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;


@Entity
@Table(name = "ingredients")
public class Ingredient {

    // 期限がこの日数以内なら「期限が近い」とする（在庫チェックなどでも共通で使う）
    public static final int EXPIRING_DAYS = 3;

    /** カテゴリ（表示する順番）。一覧のチップ、フォームの選択肢、入力チェックで使い回す */
    public static final List<String> CATEGORIES = List.of(
            "肉", "魚介", "野菜", "果物", "きのこ", "卵・乳製品",
            "豆腐・大豆製品", "米・パン・麺", "調味料", "飲み物", "冷凍食品", "その他");

    /** カテゴリごとのアイコン（絵文字） */
    public static final Map<String, String> CATEGORY_ICONS = Map.ofEntries(
            Map.entry("肉", "🥩"),
            Map.entry("魚介", "🐟"),
            Map.entry("野菜", "🥕"),
            Map.entry("果物", "🍎"),
            Map.entry("きのこ", "🍄"),
            Map.entry("卵・乳製品", "🥚"),
            Map.entry("豆腐・大豆製品", "🫘"),
            Map.entry("米・パン・麺", "🍞"),
            Map.entry("調味料", "🧂"),
            Map.entry("飲み物", "🥤"),
            Map.entry("冷凍食品", "🧊"),
            Map.entry("その他", "🛒"));

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;            // 番号（自動で振られる）

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn (name = "user_id", nullable = false)
    private AppUser user;      //この食材の持ち主

    private String name;        // 食材名
    private Double quantity;    // 数量
    private String unit;        // 単位
    private String category;    // カテゴリ
    private String expiryType;  // 賞味期限 or 消費期限

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate expiryDate;   // 期限日

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate openedDate;   // 開封日（任意）

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Double getQuantity() { return quantity; }
    public void setQuantity(Double quantity) { this.quantity = quantity; }
    // 表示用：2.0 → 2、0.5 → 0.5
    public String getQuantityText() {
        if (quantity == null) {
            return "";
        }
        return new DecimalFormat("0.###").format(quantity);
    }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getExpiryType() { return expiryType; }
    public void setExpiryType(String expiryType) { this.expiryType = expiryType; }

    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }

    public LocalDate getOpenedDate() { return openedDate; }
    public void setOpenedDate(LocalDate openedDate) { this.openedDate = openedDate; }

    // 数量を変更する（0未満にはしない）
    public void changeQuantity(double newQuantity) {
        this.quantity = Math.max(0, newQuantity);
    }
    /**
     * 期限切れか（期限日が今日より前）。
     */
    public boolean isExpired() {
        return expiryDate != null && expiryDate.isBefore(LocalDate.now());
    }

    /**
     * 期限が近いか（期限切れも含め、今日から EXPIRING_DAYS 日以内）。
     */
    public boolean isExpiringSoon() {
        return expiryDate != null && !expiryDate.isAfter(LocalDate.now().plusDays(EXPIRING_DAYS));
    }

    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }

    /**
     * 期限まであと何日か。今日が期限なら 0、期限を過ぎていればマイナス（例：2日過ぎ → -2）。
     * 画面では ${item.daysLeft} で呼び出せる。
     */
    public long getDaysLeft() {
        // ChronoUnit.DAYS.between(A, B)：A から B まで何日か（献立の getWeek でも使った）
        return expiryDate == null ? 0 : ChronoUnit.DAYS.between(LocalDate.now(), expiryDate);
    }

    /** カテゴリのアイコン。表に無いカテゴリなら 🛒。画面では ${item.categoryIcon} */
    public String getCategoryIcon() {
        return CATEGORY_ICONS.getOrDefault(category, "🛒");
    }
}