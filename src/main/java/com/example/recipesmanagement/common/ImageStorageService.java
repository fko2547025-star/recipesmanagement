package com.example.recipesmanagement.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * アップロードされた写真を保存するクラス。
 *
 * 写真は DB には入れず、app.upload-dir のフォルダにファイルとして保存し、
 * DB にはファイル名だけを記録する。
 */
@Service
public class ImageStorageService {

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
     */
    public boolean isSupported(MultipartFile file) {
        String type = file.getContentType();
        return type != null && EXTENSIONS.containsKey(type);
    }

    /**
     * 写真を保存して、保存したファイル名を返す。
     *
     * ファイル名は元の名前を使わず、ランダムな名前（UUID）にする。理由：
     * ・同じ名前の写真（IMG_0001.jpg など）が上書きされるのを防ぐ
     * ・元のファイル名に「../」などが入っていても、変な場所に保存されないようにする
     */
    public String store(MultipartFile file) {
        String extension = EXTENSIONS.get(file.getContentType());
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
            throw new UncheckedIOException("写真の削除に失敗しました", e);
        }
    }

    // 保存先フォルダ（WebConfig で使う）
    public Path getUploadDir() {
        return uploadDir;
    }
}