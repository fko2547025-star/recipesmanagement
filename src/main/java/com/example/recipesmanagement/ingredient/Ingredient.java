package com.example.recipesmanagement.ingredient;

import java.time.LocalDate;
import java.text.DecimalFormat;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;


@Entity
@Table(name = "ingredients")
public class Ingredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;            // 番号（自動で振られる）

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
}