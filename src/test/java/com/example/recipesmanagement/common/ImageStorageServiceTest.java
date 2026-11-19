package com.example.recipesmanagement.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

/**
 * 写真の保存（ImageStorageService）のテスト。中身が本当に画像かを確かめているか。
 */
class ImageStorageServiceTest {

    // PNG の先頭の8バイト（どの PNG ファイルも、この印から始まる）＋ てきとうな中身
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};
    private static final byte[] GIF = "GIF89a......".getBytes();
    private static final byte[] WEBP = "RIFF\0\0\0\0WEBPVP8 ".getBytes();

    @TempDir
    Path uploadDir;   // テストのたびに作られる一時フォルダ

    @Test
    void 本物の画像は受け付ける() throws Exception {
        ImageStorageService service = new ImageStorageService(uploadDir.toString());
        assertThat(service.isSupported(new MockMultipartFile("image", "a.png", "image/png", PNG))).isTrue();
        assertThat(service.isSupported(new MockMultipartFile("image", "a.jpg", "image/jpeg", JPEG))).isTrue();
        assertThat(service.isSupported(new MockMultipartFile("image", "a.gif", "image/gif", GIF))).isTrue();
        assertThat(service.isSupported(new MockMultipartFile("image", "a.webp", "image/webp", WEBP))).isTrue();
    }

    @Test
    void 画像と名乗っていても中身が画像でなければ受け付けない() throws Exception {
        ImageStorageService service = new ImageStorageService(uploadDir.toString());
        byte[] html = "<html><script>alert(1)</script></html>".getBytes();
        assertThat(service.isSupported(new MockMultipartFile("image", "a.jpg", "image/jpeg", html))).isFalse();
        assertThat(service.isSupported(new MockMultipartFile("image", "a.png", "image/png", new byte[0]))).isFalse();
    }

    @Test
    void 画像以外の種類は受け付けない() throws Exception {
        ImageStorageService service = new ImageStorageService(uploadDir.toString());
        assertThat(service.isSupported(new MockMultipartFile("image", "a.svg", "image/svg+xml", PNG))).isFalse();
    }

    @Test
    void 拡張子は中身から調べた種類で決める() throws Exception {
        ImageStorageService service = new ImageStorageService(uploadDir.toString());
        // 種類は JPEG と名乗っているが、中身は PNG
        String saved = service.store(new MockMultipartFile("image", "a.jpg", "image/jpeg", PNG));
        assertThat(saved).endsWith(".png");
        assertThat(uploadDir.resolve(saved)).exists();
    }
}
