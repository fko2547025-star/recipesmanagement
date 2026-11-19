package com.example.recipesmanagement.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;

/**
 * アップロードされた写真を保存するクラス。
 *
 * 写真は DB には入れず、app.upload-dir のフォルダにファイルとして保存し、
 * DB にはファイル名だけを記録する。
 */
@Service
public class ImageStorageService {

    // ログを出すための道具（System.out.println の仕事用の版）
    private static final Logger log = LoggerFactory.getLogger(ImageStorageService.class);

    /**
     * 受け付ける画像の種類と、保存するときの拡張子。
     * ここに無い種類のファイルは保存しない。
     */
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "image/gif", ".gif");

    // 写真の保存先フォルダ
    private final Path uploadDir;

    /**
     * @param uploadDir application.properties の app.upload-dir の値。
     *                  @Value("${キー}") で設定ファイルの値を受け取れる
     */
    public ImageStorageService(@Value("${app.upload-dir}") String uploadDir) throws IOException {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        // フォルダが無ければ作る（アプリ起動時に1回だけ実行される）
        Files.createDirectories(this.uploadDir);
    }

    /**
     * 受け付けている画像の種類か調べる。
     *
     * ブラウザが送ってくる種類（Content-Type）は、送る側が自由に書けるので、それだけでは信用しない。
     * ファイルの中身の先頭を見て、本当に JPEG・PNG・WebP・GIF の画像か確かめる。
     */
    public boolean isSupported(MultipartFile file) {
        String type = file.getContentType();
        return type != null && EXTENSIONS.containsKey(type) && detectType(file) != null;
    }

    /**
     * ファイルの中身の先頭の数バイト（画像の種類ごとに決まっている印）から、画像の種類を調べる。
     *
     * @return image/jpeg などの種類。画像でなければ null
     */
    private static String detectType(MultipartFile file) {
        byte[] head = new byte[12];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(head, 0, head.length);
        } catch (IOException e) {
            return null;
        }
        if (read >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
            return "image/jpeg";                          // JPEG：FF D8 FF
        }
        if (read >= 8 && Arrays.equals(Arrays.copyOf(head, 8),
                new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'})) {
            return "image/png";                           // PNG：89 50 4E 47 0D 0A 1A 0A
        }
        if (read >= 6 && (startsWith(head, "GIF87a") || startsWith(head, "GIF89a"))) {
            return "image/gif";                           // GIF：GIF87a / GIF89a
        }
        if (read >= 12 && startsWith(head, "RIFF") && new String(head, 8, 4).equals("WEBP")) {
            return "image/webp";                          // WebP：RIFF ＋ 4バイト ＋ WEBP
        }
        return null;
    }

    private static boolean startsWith(byte[] bytes, String ascii) {
        for (int i = 0; i < ascii.length(); i++) {
            if (bytes[i] != ascii.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 写真を保存して、保存したファイル名を返す。
     *
     * ファイル名は元の名前を使わず、ランダムな名前（UUID）にする。理由：
     * ・同じ名前の写真（IMG_0001.jpg など）が上書きされるのを防ぐ
     * ・元のファイル名に「../」などが入っていても、変な場所に保存されないようにする
     */
    public String store(MultipartFile file) {
        // 拡張子は、中身から調べた本当の種類で決める（送られてきた種類は使わない）
        String detected = detectType(file);
        String extension = detected == null ? null : EXTENSIONS.get(detected);
        if (extension == null) {
            throw new IllegalArgumentException("対応していない画像の種類です");
        }

        // 例：3f2a9c1e-5b7d-4e8a-9c21-7d4e5f6a8b90.jpg
        String filename = UUID.randomUUID() + extension;

        // アップロードされたデータを読み出して、フォルダに書き込む
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, uploadDir.resolve(filename));
        } catch (IOException e) {
            throw new UncheckedIOException("写真の保存に失敗しました", e);
        }
        return filename;
    }

    /**
     * 写真を削除する（レシピの編集・削除のときに使う予定）。
     */
    public void delete(String filename) {
        if (filename == null) {
            return;
        }
        try {
            Files.deleteIfExists(uploadDir.resolve(filename));
        } catch (IOException e) {
            // 古い写真を消せなくても、差し替え・削除の処理は続ける。
            // ファイルが1枚残るだけで、表示や動作には影響しない
            // （エクスプローラーのプレビューや、ほかのアプリがファイルを開いているときに起きる）
            log.warn("写真を削除できませんでした（そのまま残します）：{}", e.getMessage());
        }
    }

    // 保存先フォルダ（WebConfig で使う）
    public Path getUploadDir() {
        return uploadDir;
    }
}