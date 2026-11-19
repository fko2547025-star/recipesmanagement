package com.example.recipesmanagement.password;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.recipesmanagement.common.SecureTokens;
import com.example.recipesmanagement.user.PasswordRules;
import com.example.recipesmanagement.user.AppUser;
import com.example.recipesmanagement.user.AppUserRepository;
import com.example.recipesmanagement.user.EmailRules;

/**
 * パスワードの再設定（リセット）の処理をまとめたクラス。
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    // リンクの有効期限（分）
    public static final int EXPIRE_MINUTES = 30;

    private final PasswordResetTokenRepository tokenRepository;
    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;   // spring-boot-starter-mail を入れると使えるようになる

    // 送信元のアドレス（mail-secret.properties の spring.mail.username）
    @Value("${spring.mail.username:}")
    private String mailFrom;

    public PasswordResetService(PasswordResetTokenRepository tokenRepository,
                                AppUserRepository userRepository,
                                PasswordEncoder passwordEncoder,
                                JavaMailSender mailSender) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
    }

    /**
     * 再設定のメールを送る。
     * ユーザー名とメールアドレスが合わないときも、何もせずに終わる（画面の表示は同じにする）。
     *
     * @param baseUrl リンクの先頭（例：http://localhost:8081）
     */
    @Transactional
    public void requestReset(String username, String email, String baseUrl) {
        String name = username == null ? "" : username.trim();
        String address = EmailRules.normalize(email);
        if (name.isEmpty() || address == null) {
            return;
        }

        AppUser user = userRepository.findByUsername(name).orElse(null);
        // メールアドレスは大文字・小文字を区別しない（多くのメールサービスが区別しないため）
        if (user == null || user.getEmail() == null || !user.getEmail().equalsIgnoreCase(address)) {
            return;   // 合わないときは、理由を言わずに終わる
        }
        // 確認が済んでいないメールアドレスには送らない（他人のアドレスを登録して、そこへメールを送らせないように）
        if (!user.isEmailVerified()) {
            return;
        }

        // 前に送ったリンクは使えなくする（最後に送った1つだけ使える）
        tokenRepository.deleteByUser(user);

        // トークンを作り、ハッシュだけを保存する
        String token = SecureTokens.generate();
        tokenRepository.save(new PasswordResetToken(user, SecureTokens.hash(token),
                LocalDateTime.now().plusMinutes(EXPIRE_MINUTES)));

        sendMail(user, baseUrl + "/password/reset?token=" + token);
    }

    /**
     * リンクのトークンが使えるか調べる。使えればそのトークンを返す。
     */
    @Transactional(readOnly = true)
    public Optional<PasswordResetToken> findUsable(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return tokenRepository.findByTokenHash(SecureTokens.hash(token))
                .filter(PasswordResetToken::isUsable);
    }

    /**
     * 新しいパスワードに変える。
     *
     * @return エラーメッセージのリスト（成功したら空）
     */
    @Transactional
    public List<String> reset(String token, String newPassword, String confirmPassword) {
        List<String> errors = new ArrayList<>();

        PasswordResetToken resetToken = findUsable(token).orElse(null);
        if (resetToken == null) {
            errors.add("このリンクは使えません（期限が切れたか、すでに使われています）");
            return errors;
        }
        // パスワードのルールは、マイページのパスワード変更と同じにする
        String passwordError = PasswordRules.check(newPassword);
        if (passwordError != null) {
            errors.add(passwordError);
        }
        if (newPassword != null && !newPassword.equals(confirmPassword)) {
            errors.add("パスワードが一致しません");
        }
        if (!errors.isEmpty()) {
            return errors;
        }

        // 暗号化して保存し、トークンを使用済みにする（どちらも変更検知で自動的に UPDATE）
        AppUser user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        resetToken.setUsed(true);
        return errors;
    }

    // ===== 内部で使う処理 =====

    /**
     * 再設定のメールを送る。
     * 送れなかったときはログに残す（画面には出さない：合っているユーザーかどうかがわかってしまうため）
     */
    private void sendMail(AppUser user, String link) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(user.getEmail());
        message.setSubject("【Recipe】パスワードの再設定");
        message.setText(user.getDisplayName() + " さん\n\n"
                + "パスワードの再設定のお申し込みを受け付けました。\n"
                + "次のリンクを開いて、新しいパスワードを設定してください。\n\n"
                + link + "\n\n"
                + "このリンクの有効期限は" + EXPIRE_MINUTES + "分です。1回だけ使えます。\n"
                + "心当たりが無い場合は、このメールを無視してください（パスワードは変わりません）。\n\n"
                + "Recipe");
        try {
            mailSender.send(message);
        } catch (MailException e) {
            log.error("パスワード再設定のメールを送れませんでした（ユーザー：{}）", user.getUsername(), e);
        }
    }
}