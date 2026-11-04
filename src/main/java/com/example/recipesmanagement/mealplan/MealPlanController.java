package com.example.recipesmanagement.mealplan;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.recipesmanagement.user.AppUser;
import com.example.recipesmanagement.user.AppUserRepository;
import com.example.recipesmanagement.stock.CookingLine;

/**
 * 献立を担当するコントローラー。すべてログイン必須（SecurityConfig の anyRequest）。
 *
 * | URL                              | メソッド | 処理                         |
 * |----------------------------------|----------|------------------------------|
 * | /meal-plans?date=2026-10-27      | GET      | その日を含む週の献立         |
 * | /meal-plans/new?date=&recipeId=  | GET      | 登録画面                     |
 * | /meal-plans                      | POST     | 登録                         |
 * | /meal-plans/{id}/edit            | GET      | 編集画面                     |
 * | /meal-plans/{id}                 | POST     | 更新                         |
 * | /meal-plans/{id}/delete          | POST     | 削除                         |
 */
@Controller
@RequestMapping("/meal-plans")
public class MealPlanController {

    private final MealPlanService mealPlanService;
    private final ShoppingListService shoppingListService;
    private final AppUserRepository userRepository;

    public MealPlanController(MealPlanService mealPlanService,
                              ShoppingListService shoppingListService,
                              AppUserRepository userRepository) {
        this.mealPlanService = mealPlanService;
        this.shoppingListService = shoppingListService;
        this.userRepository = userRepository;
    }

    // ===== 1週間の献立 =====

    /**
     * @param date この日を含む週を表示する。指定が無ければ今週
     */
    @GetMapping
    public String week(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                       Principal principal,
                       Model model) {

        LocalDate today = LocalDate.now();
        LocalDate monday = MealPlanService.weekStart(date != null ? date : today);

        model.addAttribute("week", mealPlanService.getWeek(currentUser(principal), monday));
        model.addAttribute("monday", monday);
        model.addAttribute("sunday", monday.plusDays(6));
        model.addAttribute("prevWeek", monday.minusWeeks(1));
        model.addAttribute("nextWeek", monday.plusWeeks(1));
        model.addAttribute("today", today);
        return "meal-plans/index";
    }

    // ===== 登録 =====

    /**
     * @param date     初めから入れておく日付（週表示の「＋ 追加」から来たとき）
     * @param recipeId 初めから選んでおくレシピ（レシピ詳細の「献立に追加」から来たとき）
     */
    @GetMapping("/new")
    public String newForm(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                          @RequestParam(required = false) Long recipeId,
                          Principal principal,
                          Model model) {

        MealPlanForm form = new MealPlanForm();
        form.setPlanDate(date != null ? date : LocalDate.now());
        form.setRecipeId(recipeId);
        return showForm(form, null, currentUser(principal), model);
    }

    @PostMapping
    public String create(@ModelAttribute("form") MealPlanForm form, Principal principal, Model model) {
        AppUser user = currentUser(principal);

        List<String> errors = form.validate();
        if (!errors.isEmpty()) {
            model.addAttribute("errors", errors);
            return showForm(form, null, user, model);
        }

        MealPlan plan = mealPlanService.create(form, user);
        // 登録した日を含む週に戻る
        return "redirect:/meal-plans?date=" + plan.getPlanDate();
    }

    // ===== 編集・更新 =====

    @GetMapping("/{id:\\d+}/edit")
    public String editForm(@PathVariable Long id, Principal principal, Model model) {
        AppUser user = currentUser(principal);
        MealPlan plan = mealPlanService.findOwned(id, user);
        return showForm(MealPlanForm.from(plan), id, user, model);
    }

    @PostMapping("/{id:\\d+}")
    public String update(@PathVariable Long id,
                         @ModelAttribute("form") MealPlanForm form,
                         Principal principal,
                         Model model) {

        AppUser user = currentUser(principal);
        mealPlanService.findOwned(id, user);   // 先に本人確認（他人なら 404）

        List<String> errors = form.validate();
        if (!errors.isEmpty()) {
            model.addAttribute("errors", errors);
            return showForm(form, id, user, model);
        }

        MealPlan plan = mealPlanService.update(id, form, user);
        return "redirect:/meal-plans?date=" + plan.getPlanDate();
    }

    // ===== 削除 =====

    @PostMapping("/{id:\\d+}/delete")
    public String delete(@PathVariable Long id, Principal principal) {
        LocalDate date = mealPlanService.delete(id, currentUser(principal));
        return "redirect:/meal-plans?date=" + date;
    }

    // ===== 内部で使う処理 =====

    /**
     * 登録・編集フォームを表示する。
     *
     * @param planId 編集中の献立の id。登録のときは null
     */
    private String showForm(MealPlanForm form, Long planId, AppUser user, Model model) {
        model.addAttribute("form", form);
        model.addAttribute("planId", planId);
        model.addAttribute("recipes", mealPlanService.recipeOptions(user, form.getRecipeId()));
        return "meal-plans/form";
    }

    private AppUser currentUser(Principal principal) {
        return userRepository.findByUsername(principal.getName()).orElseThrow();
    }

    // ===== 作った =====

    /**
     * 「作った」の確認画面。何をどれだけ減らすかを表示する（まだ減らさない）。
     */
    @GetMapping("/{id:\\d+}/cook")
    public String cookConfirm(@PathVariable Long id, Principal principal, Model model) {
        AppUser user = currentUser(principal);
        MealPlan plan = mealPlanService.findOwned(id, user);

        // レシピが無い・すでに作った献立なら、確認画面は出さずに週表示へ
        if (plan.getRecipe() == null || plan.isCooked()) {
            return "redirect:/meal-plans?date=" + plan.getPlanDate();
        }

        model.addAttribute("plan", plan);
        model.addAttribute("lines", mealPlanService.previewCooking(id, user));
        return "meal-plans/cook";
    }

    /**
     * 「作った」にして在庫を減らす。結果はフラッシュ属性で週表示に渡す。
     */
    @PostMapping("/{id:\\d+}/cook")
    public String cook(@PathVariable Long id, Principal principal, RedirectAttributes redirectAttributes) {
        AppUser user = currentUser(principal);
        MealPlan plan = mealPlanService.findOwned(id, user);

        // 二重送信（ボタンの連打など）で、すでに作った献立なら何もしない
        if (plan.getRecipe() == null || plan.isCooked()) {
            return "redirect:/meal-plans?date=" + plan.getPlanDate();
        }

        List<CookingLine> lines = mealPlanService.cook(id, user);

        // 結果を文章にしてメッセージで表示する
        // フラッシュ属性はセッションに一時保存されるので、Entity ではなく文字列だけを渡す
        List<String> messages = new ArrayList<>();
        for (CookingLine line : lines) {
            messages.add(line.getSummary());
        }
        redirectAttributes.addFlashAttribute("cookTitle", "「" + plan.getRecipe().getTitle() + "」を作りました");
        redirectAttributes.addFlashAttribute("cookMessages", messages);

        return "redirect:/meal-plans?date=" + plan.getPlanDate();
    }

    // ===== 買い物リスト =====

    /**
     * その日を含む週の買い物リスト。
     *
     * @param date この日を含む週。指定が無ければ今週
     */
    @GetMapping("/shopping")
    public String shopping(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                           Principal principal,
                           Model model) {

        AppUser user = currentUser(principal);
        LocalDate today = LocalDate.now();
        LocalDate monday = MealPlanService.weekStart(date != null ? date : today);

        List<ShoppingItem> items = shoppingListService.build(user, monday);

        // 「買うもの」「量を確認」「足りるもの」に分ける
        model.addAttribute("toBuy", items.stream().filter(ShoppingItem::isToBuy).toList());
        model.addAttribute("toCheck", items.stream().filter(ShoppingItem::isNeedsCheck).toList());
        model.addAttribute("enough", items.stream().filter(i -> !i.isToBuy() && !i.isNeedsCheck()).toList());

        model.addAttribute("planCount", shoppingListService.targetPlans(user, monday).size());
        model.addAttribute("monday", monday);
        model.addAttribute("sunday", monday.plusDays(6));
        model.addAttribute("prevWeek", monday.minusWeeks(1));
        model.addAttribute("nextWeek", monday.plusWeeks(1));
        model.addAttribute("pastWeek", monday.plusDays(6).isBefore(today));   // 過ぎた週か
        return "meal-plans/shopping";
    }
}