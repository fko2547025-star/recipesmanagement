package com.example.recipesmanagement.follow;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.recipesmanagement.user.AppUser;

public interface FollowRepository extends JpaRepository<Follow, Long> {

    // me が target をフォローしているか
    boolean existsByFollowerAndFollowee(AppUser follower, AppUser followee);

    // フォローの1行（フォローをやめるときに使う）
    Optional<Follow> findByFollowerAndFollowee(AppUser follower, AppUser followee);

    // フォロー中の数（この人がフォローしている人数）
    long countByFollower(AppUser follower);

    // フォロワーの数（この人をフォローしている人数）
    long countByFollowee(AppUser followee);

    /**
     * この人がフォローしている人の一覧（新しくフォローした順）。
     * @EntityGraph：相手のユーザー（followee）も一緒に取り出す（N+1 を防ぐ）
     */
    @EntityGraph(attributePaths = "followee")
    List<Follow> findByFollowerOrderByIdDesc(AppUser follower);

    /** この人のフォロワーの一覧（新しくフォローされた順） */
    @EntityGraph(attributePaths = "follower")
    List<Follow> findByFolloweeOrderByIdDesc(AppUser followee);

    // この人がフォローしている人の id の一覧
    @Query("SELECT f.followee.id FROM Follow f WHERE f.follower = :user")
    List<Long> findFolloweeIds(@Param("user") AppUser user);
}