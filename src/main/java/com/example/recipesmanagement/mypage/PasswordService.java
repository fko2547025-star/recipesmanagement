package com.example.recipesmanagement.mypage;

import java.util.ArrayList;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.recipesmanagement.user.AppUser;
import com.example.recipesmanagement.user.AppUserRepository;

/**
 * パスワードの変更を担当するクラス。
 */
@Service
public class PasswordService {

    // 新しいパスワードの最低文字数（新規登録の minlength="4" と同じにする）
    public static final int MIN_LENGTH = 4;

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;   // SecurityConfig で @Bean 登録したもの

    public PasswordService(AppUserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * パスワードを変更する。問題があればエラーメッセージのリストを返す（無ければ空のリストで、変更済み）。
     *
     * @param username        ログイン中のユーザー名（自分のパスワードしか変えられないようにする）
     * @param currentPassword 今のパスワード（本人確認のため）
     * @param newPassword     新しいパスワード
     * @param confirmPassword 新しいパスワード（確認用）
     */
    @Transactional
    public List<String> change(String username, String currentPassword,
                               String newPassword, String confirmPassword) {
        AppUser user = userRepository.findByUsername(username).orElseThrow();
        List<String> errors = new ArrayList<>();

        // ① 今のパスワードが合っているか
        // matches(入力した文字, DBの値)：DBの値は暗号化されているので、equals では比べられない。
        //   DBの値の先頭の {bcrypt} や {noop} を見て、同じ方法で比べてくれる
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPassword())) {
            errors.add("今のパスワードが違います");
        }

        // ② 新しいパスワードの長さ
        if (newPassword == null || newPassword.length() < MIN_LENGTH) {
            errors.add("新しいパスワードは" + MIN_LENGTH + "文字以上にしてください");
        }
        // ③ 確認用と同じか
        else if (!newPassword.equals(confirmPassword)) {
            errors.add("新しいパスワード（確認）が一致しません");
        }
        // ④ 今と同じパスワードではないか
        else if (errors.isEmpty() && passwordEncoder.matches(newPassword, user.getPassword())) {
            errors.add("今と違うパスワードにしてください");
        }

        if (errors.isEmpty()) {
            // encode：BCrypt で暗号化する。DBには {bcrypt}$2a$10$... の形で保存される
            user.setPassword(passwordEncoder.encode(newPassword));
            // save() 不要：変更検知で自動的に UPDATE される
        }
        return errors;
    }
}