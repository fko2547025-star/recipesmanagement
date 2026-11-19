package com.example.recipesmanagement.user;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.recipesmanagement.common.SecureTokens;

/**
 * メールアドレスの確認。
 *
 * 登録したとき・メールアドレスを変えたときに、確認のリンクをメールで送る。
 * リンクを開くと「確認済み」になる。確認が済むまで、パスワード再設定のメールは送らない
 * （他人のメールアドレスを登録して、その人にメールを送りつけられないように）。
 */
@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

    // リンクの有効期限（時間）
    public static final int EXPIRE_HOURS = 24;

    private final AppUserRepository userRepository;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String mailFrom;

    // メールのリンクの先頭（PasswordResetController と同じ理由で、設定の値だけから作る）
    @Value("${app.public-url:}")
    private String publicUrl;

    public EmailVerificationService(AppUserRepository userRepository, JavaMailSender mailSender) {
        this.userRepository = userRepository;
        this.mailSender = mailSender;
    }

    /**
     * 確認が済んでいない状態にして、確認のメールを送る。メールアドレスが無ければ何もしない。
     * 呼び出す側のトランザクションの中で、user の変更が保存される。
     */
    public void startVerification(AppUser user) {
        if (user.getEmail() == null) {
            user.setEmailVerified(false);
            user.setEmailVerifyToken(null);
            user.setEmailVerifyExpires(null);
            return;
        }
        String token = SecureTokens.generate();
        user.setEmailVerified(false);
        user.setEmailVerifyToken(SecureTokens.hash(token));
        user.setEmailVerifyExpires(LocalDateTime.now().plusHours(EXPIRE_HOURS));
        sendMail(user, token);
    }

    /** マイページの「確認メールをもう一度送る」 */
    @Transactional
    public void resend(String username) {
        AppUser user = userRepository.findByUsername(username).orElseThrow();
        if (user.isEmailUnverified()) {
            startVerification(user);
        }
    }

    /**
     * リンクのトークンを確かめて、確認済みにする。
     *
     * @return 確認できたら true（トークンが違う・期限切れなら false）
     */
    @Transactional
    public boolean verify(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        AppUser user = userRepository.findByEmailVerifyToken(SecureTokens.hash(token)).orElse(null);
        if (user == null || user.getEmailVerifyExpires() == null
                || LocalDateTime.now().isAfter(user.getEmailVerifyExpires())) {
            return false;
        }
        user.setEmailVerified(true);
        user.setEmailVerifyToken(null);    // 1回だけ使える
        user.setEmailVerifyExpires(null);
        return true;
    }

    private void sendMail(AppUser user, String token) {
        if (publicUrl.isBlank()) {
            log.warn("app.public-url が設定されていないため、メールアドレスの確認メールを送りませんでした");
            return;
        }
        String link = publicUrl.replaceAll("/+$", "") + "/email/verify?token=" + token;
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(user.getEmail());
        message.setSubject("【Recipe】メールアドレスの確認");
        message.setText(user.getDisplayName() + " さん\n\n"
                + "Recipe にメールアドレスが登録されました。\n"
                + "次のリンクを開いて、メールアドレスの確認を済ませてください。\n"
                + "（確認が済むと、パスワードを忘れたときに再設定のメールを受け取れます）\n\n"
                + link + "\n\n"
                + "このリンクの有効期限は" + EXPIRE_HOURS + "時間です。\n"
                + "心当たりが無い場合は、このメールを無視してください。\n\n"
                + "Recipe");
        try {
            mailSender.send(message);
        } catch (MailException e) {
            log.error("メールアドレスの確認メールを送れませんでした（ユーザー：{}）", user.getUsername(), e);
        }
    }
}
