/*
 * レシピカードの ♡（いいね）を、ページを移動せずに切り替える。
 *
 * カードの <button class="card-like" data-url="/recipes/1/like" data-recipe="1"> を押すと、
 * Accept: application/json を付けて POST し、返ってきた {"liked": true/false, "count": 数} で
 * ハートの塗りつぶしと、同じレシピのいいね数（data-like-count="1" の要素）を書き換える。
 */
document.addEventListener('click', async (event) => {
    const button = event.target.closest('button.card-like');
    if (!button || button.disabled) return;
    event.preventDefault();

    // CSRF 対策の合言葉（head.html の meta に入っている）
    const token = document.querySelector('meta[name="_csrf"]')?.content;
    const header = document.querySelector('meta[name="_csrf_header"]')?.content;
    const headers = { 'Accept': 'application/json' };
    if (token && header) headers[header] = token;

    button.disabled = true;               // 連打で何回も送らないように
    try {
        const res = await fetch(button.dataset.url, { method: 'POST', headers });
        // ログインが切れていると、ログイン画面（HTML）が返ってくる
        if (res.redirected || !(res.headers.get('Content-Type') || '').includes('json')) {
            location.href = '/login';
            return;
        }
        if (!res.ok) throw new Error();
        const data = await res.json();

        // 同じレシピのカードがページに複数あっても、すべてそろえる
        document.querySelectorAll('button.card-like[data-recipe="' + button.dataset.recipe + '"]').forEach(b => {
            b.classList.toggle('is-liked', data.liked);
            b.setAttribute('aria-pressed', data.liked);
            b.title = data.liked ? 'いいねを取り消す' : 'いいね';
        });
        document.querySelectorAll('[data-like-count="' + button.dataset.recipe + '"]').forEach(el => {
            el.textContent = data.count;
        });

        // いいねしたときだけ、ハートをポンと弾ませる（クラスを付け直すと、アニメーションがもう一度動く）
        if (data.liked) {
            button.classList.remove('is-pop');
            void button.offsetWidth;
            button.classList.add('is-pop');
        }
    } catch (e) {
        alert('いいねできませんでした。もう一度お試しください。');
    } finally {
        button.disabled = false;
    }
});
