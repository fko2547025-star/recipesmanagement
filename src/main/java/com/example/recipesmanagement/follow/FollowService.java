package com.example.recipesmanagement.follow;

import java.util.List;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.recipesmanagement.user.AppUser;
import com.example.recipesmanagement.user.AppUserRepository;

/**
 * フォローの処理をまとめたクラス。
 */
@Service
public class FollowService {

    private final FollowRepository followRepository;
    private final AppUserRepository userRepository;

    public FollowService(FollowRepository followRepository, AppUserRepository userRepository) {
        this.followRepository = followRepository;
        this.userRepository = userRepository;
    }

    /**
     * フォローする／やめるを切り替える（いいねと同じ考え方）。
     *
     * @return 切り替えた後にフォローしていれば true
     */
    @Transactional
    public boolean toggle(AppUser me, Long targetId) {
        AppUser target = userRepository.findById(targetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        // 自分自身はフォローできない
        if (me.getId().equals(target.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }

        Optional<Follow> existing = followRepository.findByFollowerAndFollowee(me, target);
        if (existing.isPresent()) {
            followRepository.delete(existing.get());   // フォロー中 → やめる
            return false;
        }
        followRepository.save(new Follow(me, target));  // まだ → フォローする
        return true;
    }

    /**
     * me が target をフォローしているか。ログインしていない（me が null）なら false。
     */
    @Transactional(readOnly = true)
    public boolean isFollowing(AppUser me, AppUser target) {
        if (me == null) {
            return false;
        }
        return followRepository.existsByFollowerAndFollowee(me, target);
    }

    /** フォロワーの数 */
    @Transactional(readOnly = true)
    public long followerCount(AppUser user) {
        return followRepository.countByFollowee(user);
    }

    /** フォロー中の数 */
    @Transactional(readOnly = true)
    public long followingCount(AppUser user) {
        return followRepository.countByFollower(user);
    }

    /** フォロワーの一覧（Follow の行から、相手のユーザーだけを取り出す） */
    @Transactional(readOnly = true)
    public List<AppUser> followers(AppUser user) {
        return followRepository.findByFolloweeOrderByIdDesc(user).stream()
                .map(Follow::getFollower)
                .toList();
    }

    /** フォロー中の一覧 */
    @Transactional(readOnly = true)
    public List<AppUser> following(AppUser user) {
        return followRepository.findByFollowerOrderByIdDesc(user).stream()
                .map(Follow::getFollowee)
                .toList();
    }

    /**
     * フォローしている人の id（レシピ一覧の「フォロー中」で絞り込むため）。
     * Set にするのは、contains（含まれているか）を速く調べられるから
     */
    @Transactional(readOnly = true)
    public Set<Long> followeeIds(AppUser user) {
        return new HashSet<>(followRepository.findFolloweeIds(user));
    }
}