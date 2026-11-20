/*
 * 管理者画面の表を、スマホでカードの形にするための準備。
 * 表の見出し（「ユーザー」「メール」など）を、各行のセルの data-label に入れておく。
 * スマホの幅のときは、CSS（style.css の 29章）がこの data-label をセルの左に表示する。
 * 操作のボタンの欄（admin-actions）には付けない。
 */
document.querySelectorAll('.admin-table').forEach((table) => {
    const heads = [...table.querySelectorAll('thead th')].map((th) => th.textContent.trim());
    table.querySelectorAll('tbody tr').forEach((tr) => {
        [...tr.children].forEach((td, i) => {
            if (heads[i] && !td.classList.contains('admin-actions')) {
                td.dataset.label = heads[i];
            }
        });
    });
});
