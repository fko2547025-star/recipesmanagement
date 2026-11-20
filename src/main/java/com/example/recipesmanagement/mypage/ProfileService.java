package com.example.recipesmanagement.mypage;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.recipesmanagement.common.ImageStorageService;
import com.example.recipesmanagement.user.AppUser;
import com.example.recipesmanagement.user.AppUserRepository;
import com.example.recipesmanagement.user.EmailRules;
import com.example.recipesmanagement.user.EmailVerificationService;

/**
 * プロフィール（ニックネーム・写真・アイコンの枠）の変更を担当するクラス。
 */
@Service
public class ProfileService {

    // ニックネームの最大文字数（テーブルの VARCHAR(50) に合わせる）
    public static final int NICKNAME_MAX = 50;

    // アレルギー・苦手な食材の最大文字数（テーブルの VARCHAR(200) に合わせる）
    public static final int AVOID_FOODS_MAX = 200;

    private final AppUserRepository userRepository;
    private final ImageStorageService imageStorageService;
    private final EmailVerificationService verificationService;
    private final com.example.recipesmanagement.admin.NgWordService ngWordService;

    public ProfileService(AppUserRepository userRepository, ImageStorageService imageStorageService,
                          EmailVerificationService verificationService,
                          com.example.recipesmanagement.admin.NgWordService ngWordService) {
        this.ngWordService = ngWordService;
        this.userRepository = userRepository;
        this.imageStorageService = imageStorageService;
        this.verificationService = verificationService;
    }

    public List<String> validate(String nickname, String email, String avoidFoods, MultipartFile avatar) {
        List<String> errors = new ArrayList<>();
        if (nickname != null && nickname.trim().length() > NICKNAME_MAX) {
            errors.add("ニックネームは" + NICKNAME_MAX + "文字以内で入力してください");
        }
        String ngError = ngWordService.check(nickname);   // NGワードが入っていればエラー
        if (ngError != null) {
            errors.add(ngError);
        }
        if (avoidFoods != null && avoidFoods.trim().length() > AVOID_FOODS_MAX) {
            errors.add("アレルギー・苦手な食材は" + AVOID_FOODS_MAX + "文字以内で入力してください");
        }
        if (avatar != null && !avatar.isEmpty() && !imageStorageService.isSupported(avatar)) {
            errors.add("写真は JPEG・PNG・WebP・GIF のいずれかを選んでください");
        }
        String normalizedEmail = EmailRules.normalize(email);
        if (normalizedEmail != null) {
            String emailError = EmailRules.check(normalizedEmail);
            if (emailError != null) {
                errors.add(emailError);
            }
        }
        return errors;
    }

    /**
     * プロフィールを更新する。
     *
     * @param username     ログイン中のユーザー名（自分のプロフィールしか変えられないようにする）
     * @param removeAvatar 「写真を削除する」にチェックがあれば true
     * @param avatarFrame  選んだ枠の key（validate で確認済み）
     */
    @Transactional
    public void update(String username, String nickname, String email, String avoidFoods,
                       MultipartFile avatar, boolean removeAvatar) {
        AppUser user = userRepository.findByUsername(username).orElseThrow();

        user.setNickname(nickname == null || nickname.isBlank() ? null : nickname.trim());
        String newEmail = EmailRules.normalize(email);
        boolean emailChanged = newEmail == null ? user.getEmail() != null
                : !newEmail.equalsIgnoreCase(user.getEmail() == null ? "" : user.getEmail());
        user.setEmail(newEmail);
        if (emailChanged) {
            // 新しいメールアドレスの確認をやり直す（確認のメールを送る）
            verificationService.startVerification(user);
        }
        user.setAvoidFoods(avoidFoods == null || avoidFoods.isBlank() ? null : avoidFoods.trim());
        String oldAvatar = user.getAvatarPath();
        if (avatar != null && !avatar.isEmpty()) {
            user.setAvatarPath(imageStorageService.store(avatar));   // 新しい写真に差し替え
        } else if (removeAvatar) {
            user.setAvatarPath(null);                               // 写真を外す
        }

        // save() 不要：変更検知で自動的に UPDATE される

        // 使われなくなった古い写真ファイルを削除する
        if (oldAvatar != null && !oldAvatar.equals(user.getAvatarPath())) {
            imageStorageService.delete(oldAvatar);
        }
    }
}