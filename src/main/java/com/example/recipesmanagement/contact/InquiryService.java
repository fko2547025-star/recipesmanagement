package com.example.recipesmanagement.contact;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.recipesmanagement.user.AppUser;
import com.example.recipesmanagement.user.EmailRules;

/**
 * お問い合わせの受け付けと、管理者からの返信。
 */
@Service
public class InquiryService {

    private static final Logger log = LoggerFactory.getLogger(InquiryService.class);

    private final InquiryRepository repository;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String mailFrom;

    public InquiryService(InquiryRepository repository, JavaMailSender mailSender) {
        this.repository = repository;
        this.mailSender = mailSender;
    }

    /** 入力チェック。エラーメッセージのリスト（問題が無ければ空） */
    public List<String> validate(String category, String email, String message) {
        List<String> errors = new java.util.ArrayList<>();
        if (category == null || !Inquiry.CATEGORIES.containsKey(category)) {
            errors.add("種類を選んでください");
        }
        String address = EmailRules.normalize(email);
        if (address == null) {
            errors.add("返信先のメールアドレスを入力してください");
        } else {
            String emailError = EmailRules.check(address);
            if (emailError != null) {
                errors.add(emailError);
            }
        }
        if (message == null || message.isBlank()) {
            errors.add("お問い合わせの内容を入力してください");
        } else if (message.trim().length() > Inquiry.MAX_MESSAGE) {
            errors.add("お問い合わせの内容は" + Inquiry.MAX_MESSAGE + "文字以内で入力してください");
        }
        return errors;
    }

    /** 受け付ける（validate で確かめてから呼ぶ）。user はログインしていなければ null */
    @Transactional
    public void submit(String category, String email, String message, AppUser user) {
        Inquiry inquiry = new Inquiry();
        inquiry.setCategory(category);
        inquiry.setEmail(EmailRules.normalize(email));
        inquiry.setMessage(message.trim());
        inquiry.setUser(user);
        repository.save(inquiry);
    }

    @Transactional(readOnly = true)
    public List<Inquiry> list(boolean done) {
        return repository.findByStatusOrderByIdDesc(done ? "DONE" : "OPEN");
    }

    @Transactional(readOnly = true)
    public Inquiry find(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    /**
     * 返信する：相手にメールを送り、対応済みにする。
     *
     * @return エラーメッセージ（送れたら null）
     */
    @Transactional
    public String reply(Long id, String reply) {
        if (reply == null || reply.isBlank()) {
            return "返信の内容を入力してください";
        }
        if (reply.trim().length() > Inquiry.MAX_REPLY) {
            return "返信は" + Inquiry.MAX_REPLY + "文字以内で入力してください";
        }
        Inquiry inquiry = find(id);
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(mailFrom);
        mail.setTo(inquiry.getEmail());
        mail.setSubject("【CookBuddy】お問い合わせへの回答");
        mail.setText(reply.trim() + "\n\n"
                + "――――――――――――――――\n"
                + "お問い合わせの内容（" + inquiry.getCategoryLabel() + "）：\n"
                + inquiry.getMessage() + "\n"
                + "――――――――――――――――\n\n"
                + "CookBuddy 運営チーム");
        try {
            mailSender.send(mail);
        } catch (MailException e) {
            log.error("お問い合わせの返信メールを送れませんでした（id={}）", id, e);
            return "メールを送れませんでした。メールの設定を確かめてください";
        }
        inquiry.setReply(reply.trim());
        inquiry.setRepliedAt(LocalDateTime.now());
        inquiry.setStatus("DONE");
        return null;
    }

    /** 対応済み・未対応を切り替える（メールで返信しないで終えたときなど） */
    @Transactional
    public boolean toggleDone(Long id) {
        Inquiry inquiry = find(id);
        inquiry.setStatus(inquiry.isDone() ? "OPEN" : "DONE");
        return inquiry.isDone();
    }
}
