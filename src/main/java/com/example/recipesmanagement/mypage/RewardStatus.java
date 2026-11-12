package com.example.recipesmanagement.mypage;

import java.util.ArrayList;
import java.util.List;

/**
 * ごほうびの達成状況（スタンプの合計から計算する）。DB とは関係ない。
 */
public class RewardStatus {

    /** ごほうび1つ分と、もらえたかどうか */
    public static class Item {
        private final Reward reward;
        private final boolean achieved;

        public Item(Reward reward, boolean achieved) {
            this.reward = reward;
            this.achieved = achieved;
        }

        public Reward getReward() { return reward; }
        public boolean isAchieved() { return achieved; }
    }

    private final int totalStamps;          // 今までのスタンプの合計
    private final List<Item> items = new ArrayList<>();
    private Reward latest;                  // いちばん最近もらったごほうび（無ければ null）
    private Reward next;                    // 次のごほうび（全部もらっていれば null）
    private String frame;                   // 今使えるプロフィール写真の枠（無ければ null）

    public RewardStatus(int totalStamps) {
        this.totalStamps = totalStamps;
        for (Reward r : Reward.ALL) {
            boolean achieved = totalStamps >= r.getRequired();
            items.add(new Item(r, achieved));
            if (achieved) {
                latest = r;
                if (r.getFrame() != null) {
                    frame = r.getFrame();       // 後ろほど上の枠なので、上書きしていけば一番良い枠が残る
                }
            } else if (next == null) {
                next = r;                       // もらえていない最初のごほうび ＝ 次のごほうび
            }
        }
    }

    public int getTotalStamps() { return totalStamps; }
    public List<Item> getItems() { return items; }
    public Reward getLatest() { return latest; }
    public Reward getNext() { return next; }
    public String getFrame() { return frame; }

    /** 次のごほうびまで、あといくつか */
    public int getStampsToNext() { return next == null ? 0 : next.getRequired() - totalStamps; }
}