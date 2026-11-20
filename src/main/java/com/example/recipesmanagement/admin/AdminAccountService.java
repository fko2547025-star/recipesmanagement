package com.example.recipesmanagement.admin;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.recipesmanagement.user.AccountDeletionService;
import com.example.recipesmanagement.user.AppUser;
import com.example.recipesmanagement.user.AppUserRepository;
import com.example.recipesmanagement.user.EmailRules;
import com.example.recipesmanagement.user.PasswordRules;
import com.example.recipesmanagement.user.UsernameRules;

/**
 * 管理者アカウントの追加・削除（管理者画面）。
 *
 * 管理者はユーザーの機能を使わない専用のアカウントなので、ふつうの新規登録ではなく、管理者画面から作る。
 */
@Service
public class AdminAccountService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountDeletionService accountDeletionService;

    public AdminAccountService(AppUserRepository userRepository, PasswordEncoder passwordEncoder,
                               AccountDeletionService accountDeletionService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.accountDeletionService = accountDeletionService;
    }

    @Transactional(readOnly = true)
    public List<AppUser> admins() {
        return userRepository.findByRoleOrderByIdAsc("ADMIN");
    }

    /**
     * 管理者を追加する。
     *
     * @param email 任意。入れておくと、パスワードを忘れたときに再設定のメールを受け取れる
     * @return エラーメッセージのリスト（追加できたら空）
     */
    @Transactional
    public List<String> create(String username, String email, String password, String passwordConfirm) {
        List<String> errors = new ArrayList<>();
        String name = UsernameRules.normalize(username);
        String nameError = UsernameRules.check(name);
        if (nameError != null) {
            errors.add(nameError);
        } else if (userRepository.existsByUsername(name)) {
            errors.add("そのユーザーIDはすでに使われています");
        }
        String address = EmailRules.normalize(email);
        if (address != null && EmailRules.check(address) != null) {
            errors.add(EmailRules.check(address));
        }
        String passwordError = PasswordRules.check(password);
        if (passwordError != null) {
            errors.add(passwordError);
        } else if (!password.equals(passwordConfirm)) {
            errors.add("パスワードが一致しません");
        }
        if (!errors.isEmpty()) {
            return errors;
        }

        AppUser admin = new AppUser();
        admin.setUsername(name);
        admin.setEmail(address);
        admin.setEmailVerified(true);   // 管理者が自分で入れたアドレスなので、確認済みとして扱う
        admin.setPassword(passwordEncoder.encode(password));
        admin.setRole("ADMIN");
        userRepository.save(admin);
        return errors;
    }

    /**
     * 管理者を削除する。自分自身と、最後の1人は削除できない（管理者がいなくなると、誰も管理できなくなるため）。
     *
     * @return 削除したユーザーID
     */
    @Transactional
    public String delete(Long adminId, String currentUsername) {
        AppUser admin = userRepository.findById(adminId)
                .filter(AppUser::isAdmin)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (admin.getUsername().equals(currentUsername)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "自分自身は削除できません");
        }
        if (userRepository.countByRole("ADMIN") <= 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "最後の管理者は削除できません");
        }
        accountDeletionService.delete(admin.getId());
        return admin.getUsername();
    }
}
