package com.example.recipesmanagement.common;

import java.security.Principal;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * すべてのコントローラーに共通する処理をまとめたクラス。
 *
 * ナビゲーションは全画面に表示されるので、どの画面でも
 * 「ログイン中のユーザー名」が必要になる。
 * 各コントローラーで毎回 model.addAttribute() を書く代わりに、ここで一括で渡す。
 */
// @ControllerAdvice：すべてのコントローラーに共通の処理を追加する目印
@ControllerAdvice
public class LoginUserAdvice {

    /**
     * どの画面を表示するときも、この戻り値が "loginUsername" という名前で画面に渡される。
     *
     * @param principal ログイン中の人の情報。ログインしていなければ null
     * @return ログイン中のユーザー名。ログインしていなければ null
     */
    // @ModelAttribute("名前")：メソッドに付けると、全画面の model にこの名前で値を追加する
    @ModelAttribute("loginUsername")
    public String loginUsername(Principal principal) {
        return principal != null ? principal.getName() : null;
    }
}