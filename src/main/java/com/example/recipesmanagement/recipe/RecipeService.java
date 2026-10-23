package com.example.recipesmanagement.recipe;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.example.recipesmanagement.common.ImageStorageService;
import com.example.recipesmanagement.user.AppUser;

/**
 * レシピの投稿・更新・削除の処理をまとめたクラス。
 *
 * コントローラーは「どの画面を出すか」に専念し、
 * 「レシピをどう保存するか」はこのクラスが担当する。
 *
 * @Transactional を付けたメソッドは、中の DB 操作が全部成功したら確定（コミット）、
 * 途中で例外が起きたら全部取り消し（ロールバック）になる。
 */
@Service
public class RecipeService {

    private final RecipeRepository repository;
    private final ImageStorageService imageStorageService;

    public RecipeService(RecipeRepository repository, ImageStorageService imageStorageService) {
        this.repository = repository;
        this.imageStorageService = imageStorageService;
    }

    /**
     * 写真が「選ばれていない」か「対応している種類」なら true。
     */
    public boolean isAcceptableImage(MultipartFile image) {
        return !hasFile(image) || imageStorageService.isSupported(image);
    }

    /**
     * 自分が投稿したレシピを取得する。
     *
     * ・存在しない → 404（Not Found）
     * ・他人のレシピ → 403（Forbidden：権限がありません）
     *
     * 食材管理では他人の食材も 404 にしていたが、レシピは誰でも見られる公開情報なので、
     * 「存在するけれど、あなたは編集できません」という 403 のほうが正確。
     *
     * readOnly = true：読み取り専用のトランザクション（更新しないことを明示する）
     */
    @Transactional(readOnly = true)
    public Recipe findOwned(Long id, AppUser user) {
        Recipe recipe = repository.findWithDetailsById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        // 投稿者の id とログイン中のユーザーの id を比べる
        if (!recipe.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return recipe;
    }

    /**
     * レシピを投稿する。
     *
     * @return 保存したレシピ（id が振られている）
     */
    @Transactional
    public Recipe create(RecipeForm form, MultipartFile image, AppUser user) {
        Recipe recipe = new Recipe();
        recipe.setUser(user);   // 投稿者はログイン中のユーザー
        applyForm(recipe, form);

        if (hasFile(image)) {
            recipe.setImagePath(imageStorageService.store(image));
        }

        // cascade = ALL なので、材料も一緒に INSERT される
        return repository.save(recipe);
    }

    /**
     * レシピを更新する。
     *
     * @param removeImage 「写真を削除する」にチェックが入っていれば true
     */
    @Transactional
    public void update(Long id, RecipeForm form, MultipartFile image, boolean removeImage, AppUser user) {
        // 自分のレシピか確認しながら取得
        Recipe recipe = findOwned(id, user);

        applyForm(recipe, form);
        recipe.setUpdatedAt(LocalDateTime.now());

        // ----- 写真 -----
        String oldImage = recipe.getImagePath();
        if (hasFile(image)) {
            // 新しい写真が選ばれていれば差し替え
            recipe.setImagePath(imageStorageService.store(image));
        } else if (removeImage) {
            // 「写真を削除する」にチェックがあれば外す
            recipe.setImagePath(null);
        }

        // repository.save() は書いていない！
        // @Transactional の中で DB から取得した Entity は JPA が見張っていて、
        // 値を変更すると、メソッドの終わり（コミット時）に自動で UPDATE される（変更検知）

        // 使われなくなった古い写真ファイルを削除
        if (oldImage != null && !oldImage.equals(recipe.getImagePath())) {
            imageStorageService.delete(oldImage);
        }
    }

    /**
     * レシピを削除する。
     */
    @Transactional
    public void delete(Long id, AppUser user) {
        Recipe recipe = findOwned(id, user);
        String image = recipe.getImagePath();

        // 材料は cascade で一緒に削除される
        // いいね・コメントは、テーブル作成時の ON DELETE CASCADE で DB が自動で削除する
        // 献立は ON DELETE SET NULL で、レシピの欄だけ空になる
        repository.delete(recipe);

        imageStorageService.delete(image);
    }

    // ===== 内部で使う処理 =====

    /**
     * フォームの値をレシピに写す（投稿・更新で共通）。
     */
    private void applyForm(Recipe recipe, RecipeForm form) {
        recipe.setTitle(form.getTitle().trim());
        recipe.setCategory(form.getCategory());
        recipe.setDescription(blankToNull(form.getDescription()));
        recipe.setSteps(form.getSteps().trim());

        // 材料は「全部外してから、入力された行を入れ直す」
        // orphanRemoval = true なので、リストから外した古い材料は DB からも自動で DELETE される
        recipe.getIngredients().clear();

        int order = 1;
        List<String> names = form.getIngredientNames();
        for (int i = 0; i < names.size(); i++) {
            if (isBlank(names.get(i))) {
                continue;   // 名前が空の行は飛ばす
            }
            RecipeIngredient ingredient = new RecipeIngredient();
            ingredient.setName(names.get(i).trim());
            ingredient.setQuantity(parseQuantity(valueAt(form.getIngredientQuantities(), i)));
            ingredient.setUnit(blankToNull(valueAt(form.getIngredientUnits(), i)));
            ingredient.setSortOrder(order++);
            recipe.addIngredient(ingredient);
        }
    }

    private boolean hasFile(MultipartFile file) {
        return file != null && !file.isEmpty();
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String blankToNull(String s) {
        return isBlank(s) ? null : s.trim();
    }

    private String valueAt(List<String> list, int i) {
        return i < list.size() ? list.get(i) : null;
    }

    private Double parseQuantity(String s) {
        return isBlank(s) ? null : Double.valueOf(s.trim());
    }
}