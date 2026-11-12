package com.example.recipesmanagement.mypage;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.recipesmanagement.common.ImageStorageService;
import com.example.recipesmanagement.user.AppUser;
import com.example.recipesmanagement.user.AppUserRepository;

/**
 * プロフィール（ニックネーム・写真）の変更を担当するクラス。
 */
@Service
public class ProfileService {

    // ニックネームの最大文字数（テーブルの VARCHAR(50) に合わせる）
    public static final int NICKNAME_MAX = 50;

    private final AppUserRepository userRepository;
    private final ImageStorageService imageStorageService;

    public ProfileService(AppUserRepository userRepository, ImageStorageService imageStorageService) {
        this.userRepository = userRepository;
        this.imageStorageService = imageStorageService;
    }

    /**
     * 入力チェック。問題があればエラーメッセージのリストを返す（無ければ空のリスト）。
     */
    public List<String> validate(String nickname, MultipartFile avatar) {
        List<String> errors = new ArrayList<>();
        if (nickname != null && nickname.trim().length() > NICKNAME_MAX) {
            errors.add("ニックネームは" + NICKNAME_MAX + "文字以内で入力してください");
        }
        if (avatar != null && !avatar.isEmpty() && !imageStorageService.isSupported(avatar)) {
            errors.add("写真は JPEG・PNG・WebP・GIF のいずれかを選んでください");
        }
        return errors;
    }

    /**
     * プロフィールを更新する。
     *
     * @param username     ログイン中のユーザー名（自分のプロフィールしか変えられないようにする）
     * @param nickname     新しいニックネーム。空なら null にして、ユーザー名を表示する状態に戻す
     * @param avatar       新しい写真（選ばれていなければ空）
     * @param removeAvatar 「写真を削除する」にチェックがあれば true
     */
    @Transactional
    public void update(String username, String nickname, MultipartFile avatar, boolean removeAvatar) {
        AppUser user = userRepository.findByUsername(username).orElseThrow();

        user.setNickname(nickname == null || nickname.isBlank() ? null : nickname.trim());

        String oldAvatar = user.getAvatarPath();
        if (avatar != null && !avatar.isEmpty()) {
            user.setAvatarPath(imageStorageService.store(avatar));   // 新しい写真に差し替え
        } else if (removeAvatar) {
            user.setAvatarPath(null);                               // 写真を外す
        }

        // save() は書かなくてよい：@Transactional の中で取得した Entity は、
        // 値を変えるとメソッドの終わりに自動で UPDATE される（RecipeService の update と同じ）

        // 使われなくなった古い写真ファイルを削除する
        if (oldAvatar != null && !oldAvatar.equals(user.getAvatarPath())) {
            imageStorageService.delete(oldAvatar);
        }
    }
}