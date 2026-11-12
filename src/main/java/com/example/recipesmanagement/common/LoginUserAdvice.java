package com.example.recipesmanagement.common;
import com.example.recipesmanagement.user.AppUser;
import com.example.recipesmanagement.user.AppUserRepository;

import java.security.Principal;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import jakarta.servlet.http.HttpServletRequest;

/**
 * すべてのコントローラーに共通する処理をまとめたクラス。
 *
 * ヘッダーは全画面に表示されるので、どの画面でも
 * 「ログイン中のユーザー名」と「今開いているページの URL」が必要になる。
 * 各コントローラーで毎回 model.addAttribute() を書く代わりに、ここで一括で渡す。
 */
// @ControllerAdvice：すべてのコントローラーに共通の処理を追加する目印
@ControllerAdvice
public class LoginUserAdvice {

    private final AppUserRepository userRepository;

    public LoginUserAdvice(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * ログイン中のユーザー名。ログインしていなければ null。
     * ヘッダーで「ログイン中だけのメニュー」を出し分けるのに使う。
     */
    // @ModelAttribute("名前")：メソッドに付けると、全画面の model にこの名前で値を追加する
    @ModelAttribute("loginUsername")
    public String loginUsername(Principal principal) {
        return principal != null ? principal.getName() : null;
    }

    /**
     * ログイン中のユーザー（ヘッダーにニックネームとプロフィール写真を出すため）。
     * 全画面で ${loginUser} として使える。ログインしていなければ null。
     */
    @ModelAttribute("loginUser")
    public AppUser loginUser(Principal principal) {
        if (principal == null) {
            return null;
        }
        return userRepository.findByUsername(principal.getName()).orElse(null);
    }

    /**
     * 今開いているページの URL（例：/meal-plans）。
     * ヘッダーで、今いるページのメニューを目立たせるのに使う。
     *
     * HttpServletRequest：Servlet のときと同じ request。引数に書くと Spring が渡してくれる
     * getRequestURI()：URL のうち、ドメインの後ろの部分（?以降のパラメータは含まない）
     */
    @ModelAttribute("currentPath")
    public String currentPath(HttpServletRequest request) {
        return request.getRequestURI();
    }
}
