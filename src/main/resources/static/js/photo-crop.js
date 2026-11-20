/*
 * 写真の切り抜き（トリミング）と縮小。
 *
 * 使い方：写真を選ぶ input に data-crop を付けて、このファイルを読み込むだけ。
 *   <input type="file" name="image" data-crop="4/3" onchange="previewPhoto(this)">
 *   <script src="/js/photo-crop.js"></script>
 *
 *   data-crop       切り抜く形（横/縦）。"4/3" や "1/1"
 *   data-crop-round 付けると、丸い枠で見せる（プロフィール写真用。切り抜く形は正方形のまま）
 *   data-crop-max   切り抜いたあとの横幅の最大（px）。省略すると 1600
 *
 * しくみ：
 *   1. 写真が選ばれると、元の onchange より先にここで受け取り、切り抜き画面を開く
 *   2. 「決定」を押すと、選んだ範囲を canvas に描いて JPEG にし、input の中身をその写真に入れ替える
 *      （撮影場所などの写真の情報は、この時点で消える。大きい写真も小さくなるので、5MB を超えない）
 *   3. 入れ替えたあと、元の onchange（見本の表示など）を動かす
 *   GIF（動く画像）は、切り抜くと動かなくなるので、そのまま元の onchange に渡す。
 */
(() => {
    'use strict';

    const MAX_ZOOM = 4;            // いちばん大きくしたとき（枠いっぱいの何倍か）
    const JPEG_QUALITY = 0.88;     // 保存するときの画質（0〜1）
    const DEFAULT_MAX = 1600;      // 切り抜いたあとの横幅の最大

    // DataTransfer で input の中身を入れ替えられないブラウザ（とても古いもの）では、何もしない
    let canReplaceFiles = true;
    try {
        new DataTransfer();
    } catch (e) {
        canReplaceFiles = false;
    }

    // ===== 写真が選ばれたとき =====
    // capture（true）：画面全体で先に受け取る。ここで止めると、input の onchange はまだ動かない
    document.addEventListener('change', (event) => {
        const input = event.target;
        if (!(input instanceof HTMLInputElement) || input.type !== 'file' || !input.dataset.crop) {
            return;
        }
        if (input.dataset.cropDone === '1') {       // 切り抜いたあとに自分で起こした change は、そのまま通す
            delete input.dataset.cropDone;
            return;
        }
        const file = input.files[0];
        if (!file || !canReplaceFiles || file.type === 'image/gif' || !file.type.startsWith('image/')) {
            return;                                 // 元の onchange に任せる
        }
        event.stopPropagation();                    // 元の onchange は、切り抜きが終わってから動かす
        openCropper(input, file);
    }, true);

    // ===== 切り抜き画面 =====
    function openCropper(input, file) {
        const [rw, rh] = input.dataset.crop.split('/').map(Number);
        const ratio = rw / rh;
        const maxWidth = Number(input.dataset.cropMax) || DEFAULT_MAX;
        const url = URL.createObjectURL(file);

        // 画面の部品を作る
        const modal = document.createElement('div');
        modal.className = 'crop-modal';
        modal.innerHTML = `
            <div class="crop-dialog" role="dialog" aria-modal="true" aria-labelledby="cropTitle">
                <h2 id="cropTitle" class="crop-dialog__title">写す範囲を決める</h2>
                <p class="crop-dialog__hint">ドラッグで動かす・スライダーやホイール（スマホは2本指）で拡大</p>
                <div class="crop-stage${input.dataset.cropRound !== undefined ? ' is-round' : ''}">
                    <img class="crop-stage__img" alt="">
                </div>
                <div class="crop-tools">
                    <span class="material-symbols-rounded">zoom_out</span>
                    <input type="range" class="crop-zoom" min="1" max="${MAX_ZOOM}" step="0.01" value="1" aria-label="拡大">
                    <span class="material-symbols-rounded">zoom_in</span>
                    <button type="button" class="crop-rotate btn--outline btn--sm" title="90度回転">
                        <span class="material-symbols-rounded">rotate_right</span>回転
                    </button>
                </div>
                <div class="crop-actions">
                    <button type="button" class="crop-ok btn--wide">決定</button>
                    <button type="button" class="crop-cancel btn btn--outline">キャンセル</button>
                </div>
            </div>`;
        document.body.appendChild(modal);
        document.body.classList.add('is-crop-open');   // 後ろの画面がスクロールしないように

        const stage = modal.querySelector('.crop-stage');
        const img = modal.querySelector('.crop-stage__img');
        const zoomInput = modal.querySelector('.crop-zoom');
        stage.style.aspectRatio = `${rw} / ${rh}`;

        // 状態：回転（0〜3 ×90度）、拡大（1＝枠いっぱい）、写真の中心のずれ（枠の px）
        let rotation = 0;
        let zoom = 1;
        let offsetX = 0;
        let offsetY = 0;

        // 回転したあとの写真の縦横（90度・270度なら入れ替わる）
        const rotatedSize = () => rotation % 2 === 0
            ? [img.naturalWidth, img.naturalHeight]
            : [img.naturalHeight, img.naturalWidth];

        // 枠にちょうど収まる（すき間ができない）ときの倍率
        const coverScale = () => {
            const [w, h] = rotatedSize();
            return Math.max(stage.clientWidth / w, stage.clientHeight / h);
        };

        // 写真を動かしすぎて、枠にすき間ができないようにする
        const clamp = () => {
            const [w, h] = rotatedSize();
            const scale = coverScale() * zoom;
            const maxX = Math.max(0, (w * scale - stage.clientWidth) / 2);
            const maxY = Math.max(0, (h * scale - stage.clientHeight) / 2);
            offsetX = Math.min(maxX, Math.max(-maxX, offsetX));
            offsetY = Math.min(maxY, Math.max(-maxY, offsetY));
        };

        const render = () => {
            clamp();
            const scale = coverScale() * zoom;
            img.style.transform =
                `translate(-50%, -50%) translate(${offsetX}px, ${offsetY}px) rotate(${rotation * 90}deg) scale(${scale})`;
        };

        const setZoom = (value) => {
            zoom = Math.min(MAX_ZOOM, Math.max(1, value));
            zoomInput.value = zoom;
            render();
        };

        // ----- ドラッグ・2本指の拡大 -----
        const pointers = new Map();     // 押している指（またはマウス）の位置
        let pinchStart = null;          // 2本指を置いたときの、指の間の長さと拡大

        const distance = () => {
            const [a, b] = [...pointers.values()];
            return Math.hypot(a.x - b.x, a.y - b.y);
        };

        stage.addEventListener('pointerdown', (e) => {
            stage.setPointerCapture(e.pointerId);
            pointers.set(e.pointerId, { x: e.clientX, y: e.clientY });
            if (pointers.size === 2) {
                pinchStart = { distance: distance(), zoom };
            }
        });
        stage.addEventListener('pointermove', (e) => {
            const prev = pointers.get(e.pointerId);
            if (!prev) {
                return;
            }
            const now = { x: e.clientX, y: e.clientY };
            pointers.set(e.pointerId, now);
            if (pointers.size === 1) {
                offsetX += now.x - prev.x;
                offsetY += now.y - prev.y;
                render();
            } else if (pointers.size === 2 && pinchStart) {
                setZoom(pinchStart.zoom * distance() / pinchStart.distance);
            }
        });
        const release = (e) => {
            pointers.delete(e.pointerId);
            if (pointers.size < 2) {
                pinchStart = null;
            }
        };
        stage.addEventListener('pointerup', release);
        stage.addEventListener('pointercancel', release);

        // マウスのホイールで拡大・縮小
        stage.addEventListener('wheel', (e) => {
            e.preventDefault();
            setZoom(zoom * (e.deltaY < 0 ? 1.1 : 1 / 1.1));
        }, { passive: false });

        zoomInput.addEventListener('input', () => setZoom(Number(zoomInput.value)));

        modal.querySelector('.crop-rotate').addEventListener('click', () => {
            rotation = (rotation + 1) % 4;
            offsetX = 0;
            offsetY = 0;
            render();
        });

        // 画面の大きさが変わったら、置き直す
        const onResize = () => render();
        window.addEventListener('resize', onResize);

        // ----- 閉じる -----
        const close = () => {
            window.removeEventListener('resize', onResize);
            document.removeEventListener('keydown', onKey);
            document.body.classList.remove('is-crop-open');
            modal.remove();
            URL.revokeObjectURL(url);
        };

        // 元の onchange（見本の表示など）を動かす
        const fireChange = () => {
            input.dataset.cropDone = '1';
            input.dispatchEvent(new Event('change', { bubbles: true }));
        };

        const cancel = () => {
            close();
            input.value = '';      // 写真を選ばなかったことにする
            fireChange();
        };

        const onKey = (e) => {
            if (e.key === 'Escape') {
                cancel();
            }
        };
        document.addEventListener('keydown', onKey);
        modal.querySelector('.crop-cancel').addEventListener('click', cancel);

        // ----- 決定：選んだ範囲を canvas に描いて、JPEG にする -----
        modal.querySelector('.crop-ok').addEventListener('click', () => {
            const stageW = stage.clientWidth;
            const stageH = stage.clientHeight;
            const scale = coverScale() * zoom;
            // 元の写真で何 px 分の範囲か。それより大きくはしない（ぼやけるだけなので）
            const outW = Math.max(1, Math.round(Math.min(maxWidth, stageW / scale)));
            const outH = Math.max(1, Math.round(outW / ratio));
            const k = outW / stageW;      // 画面の 1px が、保存する写真の何 px か

            const canvas = document.createElement('canvas');
            canvas.width = outW;
            canvas.height = outH;
            const ctx = canvas.getContext('2d');
            ctx.fillStyle = '#fff';       // 透明な部分は白にする（JPEG は透明を扱えない）
            ctx.fillRect(0, 0, outW, outH);
            ctx.imageSmoothingQuality = 'high';
            // 画面と同じ順番で、動かす → 回す → 拡大する
            ctx.translate(outW / 2 + offsetX * k, outH / 2 + offsetY * k);
            ctx.rotate(rotation * Math.PI / 2);
            ctx.scale(scale * k, scale * k);
            ctx.drawImage(img, -img.naturalWidth / 2, -img.naturalHeight / 2);

            canvas.toBlob((blob) => {
                close();
                if (!blob) {
                    fireChange();         // 作れなかったときは、元の写真のまま
                    return;
                }
                const name = file.name.replace(/\.[^.]+$/, '') + '.jpg';
                const transfer = new DataTransfer();
                transfer.items.add(new File([blob], name, { type: 'image/jpeg' }));
                input.files = transfer.files;
                fireChange();
            }, 'image/jpeg', JPEG_QUALITY);
        });

        // 写真を読み込んだら、表示する
        img.onload = () => render();
        img.onerror = () => {         // 読めない写真は、切り抜かずに元の onchange に任せる
            close();
            fireChange();
        };
        img.src = url;
    }
})();
