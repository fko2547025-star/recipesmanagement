package com.example.recipesmanagement.admin;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * NGワードのチェック。
 *
 * 全角・半角、大文字・小文字の違いではすり抜けられないよう、比べる前に文字をそろえる（NFKC ＋ 小文字）。
 */
@Service
public class NgWordService {

    private final NgWordRepository repository;

    public NgWordService(NgWordRepository repository) {
        this.repository = repository;
    }

    /**
     * 文章の中に NGワードがあれば、そのエラーメッセージを返す。無ければ null。
     *
     * @param texts 調べる文章（null は飛ばす）
     */
    @Transactional(readOnly = true)
    public String check(String... texts) {
        return check(java.util.Arrays.asList(texts == null ? new String[0] : texts));   // null を含んでもよい（List.of は null を受け付けない）
    }

    @Transactional(readOnly = true)
    public String check(List<String> texts) {
        List<NgWord> words = repository.findAll();
        if (words.isEmpty()) {
            return null;
        }
        for (String text : texts) {
            if (text == null || text.isBlank()) {
                continue;
            }
            String normalized = normalize(text);
            for (NgWord word : words) {
                if (normalized.contains(normalize(word.getWord()))) {
                    return "使えない言葉（" + word.getWord() + "）が含まれています。書き直してください";
                }
            }
        }
        return null;
    }

    /** 比べるために文字をそろえる（全角英数→半角、大文字→小文字、空白を取る） */
    static String normalize(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }
}
