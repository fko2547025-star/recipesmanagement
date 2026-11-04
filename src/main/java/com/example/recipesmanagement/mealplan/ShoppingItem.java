package com.example.recipesmanagement.mealplan;

import com.example.recipesmanagement.stock.StockCheckItem;
import com.example.recipesmanagement.stock.StockStatus;

/**
 * 買い物リストの1行分。
 * 在庫チェックの結果（合計した量で比べたもの）と、その材料を使う献立の一覧。
 */
public class ShoppingItem {

    private final StockCheckItem check;   // 在庫チェックの結果
    private final String usedIn;          // 使う献立（例：豚の生姜焼き（10/30）、親子丼（10/31））

    public ShoppingItem(StockCheckItem check, String usedIn) {
        this.check = check;
        this.usedIn = usedIn;
    }

    /**
     * 買う必要があるか（足りない・在庫なし）。
     */
    public boolean isToBuy() {
        return check.isProblem();
    }

    /**
     * 単位が違うため、足りるかどうか自分で確認が必要か。
     */
    public boolean isNeedsCheck() {
        return check.getStatus() == StockStatus.UNIT_MISMATCH;
    }

    /**
     * 買う量。在庫なしなら必要な量そのもの、足りないなら不足分。
     */
    public String getBuyText() {
        if (check.getStatus() == StockStatus.SHORT) {
            return "あと" + check.getShortageText();
        }
        return check.getNeededText();
    }

    public StockCheckItem getCheck() { return check; }
    public String getUsedIn() { return usedIn; }
}