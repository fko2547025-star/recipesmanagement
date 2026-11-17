/*
 * AI（Ollama）にフォームの内容を送って、回答を画面に表示する共通の処理。
 *
 * 使い方：
 *   <form th:action="@{/ai/...}" method="post" data-answer="回答を出す要素のid" onsubmit="submitAi(event)">
 *     ...入力欄...
 *     <button type="submit">送信</button>
 *   </form>
 *   <p id="回答を出す要素のid" class="recipe-memo__text is-hidden"></p>
 *
 * th:action を使うと、CSRF 対策の隠し項目（_csrf）が自動でフォームに入るので、それも一緒に送られる。
 */
async function submitAi(event) {
    event.preventDefault();                 // 普通のフォーム送信（画面の移動）を止める
    const form = event.target;
    const button = form.querySelector('button[type="submit"]');
    const answer = document.getElementById(form.dataset.answer);

    button.disabled = true;                 // 回答が来るまで、二重に送れないようにする
    answer.classList.remove('is-hidden', 'text-danger');
    answer.textContent = 'AIが考えています…（数十秒かかることがあります）';

    try {
        // FormData：フォームの中身（入力欄と _csrf）をまとめて送る
        const res = await fetch(form.action, { method: 'POST', body: new URLSearchParams(new FormData(form)) });
        // ログインが切れていると、ログイン画面（HTML）が返ってくる
        if (res.redirected || !(res.headers.get('Content-Type') || '').includes('json')) {
            throw new Error('ログインが切れました。ページを読み込み直してください。');
        }
        const data = await res.json();
        if (data.error) {
            throw new Error(data.error);
        }
        answer.textContent = data.answer;   // textContent：HTML として解釈せず、文字としてそのまま表示
    } catch (e) {
        answer.classList.add('text-danger');
        answer.textContent = e.message || 'エラーが起きました。もう一度お試しください。';
    } finally {
        button.disabled = false;
    }
}
