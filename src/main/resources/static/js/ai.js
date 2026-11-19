/*
 * AI（Ollama）にフォームの内容を送って、回答を画面に表示する共通の処理。
 *
 * 使い方：
 *   <form th:action="@{/ai/...}" method="post" data-answer="回答を出す要素のid" onsubmit="submitAi(event)">
 *     ...入力欄...
 *     <button type="submit">送信</button>
 *   </form>
 *   <div id="回答を出す要素のid" class="is-hidden"></div>
 *
 * th:action を使うと、CSRF 対策の隠し項目（_csrf）が自動でフォームに入るので、それも一緒に送られる。
 *
 * サーバーからの返事は2種類：
 *   - まとめて返す（JSON）：{"answer": "..."} か {"error": "..."}
 *   - 少しずつ返す（1行に1つの JSON）：{"text": "続き"} が何回も届く。途中で失敗したら {"error": "..."}
 *
 * フォームに data-conversation="true" を付けると「会話」になる：
 *   質問と回答を画面に並べ、続けて質問したときは、これまでの会話（直近3組）も一緒に送る。
 *
 * フォームに data-onanswer="関数名" を付けると、回答（JSON 全体）が届いたあとにその関数を呼ぶ。
 */
const AI_THINKING = 'AIが考えています…（数十秒かかることがあります）';
const AI_HISTORY_MAX = 3;

async function submitAi(event) {
    event.preventDefault();                 // 普通のフォーム送信（画面の移動）を止める
    const form = event.target;
    const button = form.querySelector('button[type="submit"]');
    const box = document.getElementById(form.dataset.answer);
    const conversation = form.dataset.conversation === 'true';

    const params = new URLSearchParams(new FormData(form));   // 入力欄と _csrf
    let target = box;                                          // 回答を書き込む場所
    let question = '';

    if (conversation) {
        question = (form.querySelector('[name="question"]').value || '').trim();
        if (!question) return;
        // これまでの会話を一緒に送る（サーバー側でも直近3組だけ使う）
        aiHistory(form).forEach(turn => {
            params.append('historyQuestions', turn.q);
            params.append('historyAnswers', turn.a);
        });
        // 画面に「質問」と「回答の入れ物」を足す
        box.appendChild(aiBubble('ai-chat__q', question));
        target = box.appendChild(aiBubble('ai-chat__a', ''));
        form.querySelector('.ai-chat__reset')?.classList.remove('is-hidden');
    }

    button.disabled = true;                 // 回答が来るまで、二重に送れないようにする
    box.classList.remove('is-hidden');
    target.classList.remove('text-danger');
    target.textContent = AI_THINKING;
    target.scrollIntoView({ block: 'nearest' });

    try {
        const res = await fetch(form.action, { method: 'POST', body: params });
        const type = res.headers.get('Content-Type') || '';
        // ログインが切れていると、ログイン画面（HTML）が返ってくる
        if (res.redirected || !type.includes('json')) {
            throw new Error('ログインが切れました。ページを読み込み直してください。');
        }

        let answer;
        if (type.includes('ndjson')) {
            answer = await readAiStream(res, target);
        } else {
            const data = await res.json();
            if (data.error) throw new Error(data.error);
            answer = data.answer || '';
            target.textContent = answer;    // textContent：HTML として解釈せず、文字としてそのまま表示
            if (form.dataset.onanswer && window[form.dataset.onanswer]) {
                window[form.dataset.onanswer](data, form);
            }
        }

        if (conversation) {
            const history = aiHistory(form);
            history.push({ q: question, a: answer });
            if (history.length > AI_HISTORY_MAX) history.shift();
            form.querySelector('[name="question"]').value = '';
        }
    } catch (e) {
        target.classList.add('text-danger');
        target.textContent = e.message || 'エラーが起きました。もう一度お試しください。';
    } finally {
        button.disabled = false;
    }
}

/**
 * 少しずつ届く回答を読み、届いた分から target に書き足す。全部そろった回答を返す。
 */
async function readAiStream(res, target) {
    const reader = res.body.getReader();
    const decoder = new TextDecoder();     // 届いたバイトを文字にする（日本語が途中で切れても大丈夫なように stream: true）
    let buffer = '';
    let answer = '';
    for (;;) {
        const { value, done } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        // 改行までが1つの JSON。最後の行は途中かもしれないので buffer に残す
        const lines = buffer.split('\n');
        buffer = lines.pop();
        for (const line of lines) {
            if (!line.trim()) continue;
            const part = JSON.parse(line);
            if (part.error) throw new Error(part.error);
            answer += part.text || '';
            target.textContent = answer;
        }
    }
    if (!answer) throw new Error('AIから回答が得られませんでした。もう一度お試しください。');
    return answer;
}

/** フォームごとの「これまでの会話」（ページを読み込み直すと消える） */
function aiHistory(form) {
    if (!form.aiHistory) form.aiHistory = [];
    return form.aiHistory;
}

/** 会話の1つ分（質問 or 回答）の要素を作る */
function aiBubble(className, text) {
    const div = document.createElement('div');
    div.className = className;
    div.textContent = text;
    return div;
}

/** 会話をリセットする（画面の会話を消し、次の質問は新しい会話として送る） */
function resetAiConversation(button) {
    const form = button.closest('form');
    form.aiHistory = [];
    const box = document.getElementById(form.dataset.answer);
    box.innerHTML = '';
    box.classList.add('is-hidden');
    button.classList.add('is-hidden');
}
