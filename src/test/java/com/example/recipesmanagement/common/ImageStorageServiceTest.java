package com.example.recipesmanagement.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.GpsDirectory;

/**
 * 写真の保存（ImageStorageService・ImageSanitizer）のテスト。
 * 中身が本当に画像か確かめているか、撮影場所などの情報を消しているか。
 */
class ImageStorageServiceTest {

    @TempDir
    Path uploadDir;   // テストのたびに作られる一時フォルダ

    // ===== テスト用の画像を作る =====

    private static byte[] image(String format, int width, int height) throws Exception {
        BufferedImage img = new BufferedImage(width, height,
                format.equals("png") ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    /**
     * JPEG に、スマホの写真のような情報（向き＝右に90度・撮影場所）を入れる。
     * JPEG の先頭（FF D8）の直後に、APP1（Exif）のかたまりを差し込む。
     */
    private static byte[] withExif(byte[] jpeg) {
        byte[] tiff = {
                'M', 'M', 0, 42, 0, 0, 0, 8,                          // TIFF の先頭（ビッグエンディアン）、最初の目次は8バイト目
                0, 2,                                                 // 目次の項目は2つ
                0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, 6, 0, 0,             // 向き（Orientation）＝ 6（右に90度）
                (byte) 0x88, 0x25, 0, 4, 0, 0, 0, 1, 0, 0, 0, 38,     // 撮影場所の目次は38バイト目
                0, 0, 0, 0,                                           // 次の目次は無い
                0, 1,                                                 // 撮影場所の目次の項目は1つ
                0, 1, 0, 2, 0, 0, 0, 2, 'N', 0, 0, 0,                 // 北緯（GPSLatitudeRef）＝ N
                0, 0, 0, 0
        };
        byte[] header = "Exif\0\0".getBytes(StandardCharsets.US_ASCII);
        int length = 2 + header.length + tiff.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xFF);
        out.write(0xD8);
        out.write(0xFF);
        out.write(0xE1);                         // APP1
        out.write(length >> 8);
        out.write(length & 0xFF);
        out.writeBytes(header);
        out.writeBytes(tiff);
        out.write(jpeg, 2, jpeg.length - 2);     // 元の JPEG の FF D8 より後ろ
        return out.toByteArray();
    }

    /** VP8X（EXIF あり の印付き）＋ 画像のかたまり ＋ EXIF のかたまり、の WebP */
    private static byte[] webpWithExif() {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        chunk(body, "VP8X", new byte[] {0x08, 0, 0, 0, 0, 0, 0, 0, 0, 0});   // 0x08 = EXIF あり
        chunk(body, "VP8 ", new byte[] {1, 2, 3, 4});
        chunk(body, "EXIF", "GPS-SECRET".getBytes(StandardCharsets.US_ASCII));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("RIFF".getBytes(StandardCharsets.US_ASCII));
        int size = body.size() + 4;
        out.writeBytes(new byte[] {(byte) size, (byte) (size >> 8), (byte) (size >> 16), (byte) (size >> 24)});
        out.writeBytes("WEBP".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(body.toByteArray());
        return out.toByteArray();
    }

    private static void chunk(ByteArrayOutputStream out, String name, byte[] data) {
        out.writeBytes(name.getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(new byte[] {(byte) data.length, 0, 0, 0});
        out.writeBytes(data);
        if (data.length % 2 == 1) {
            out.write(0);
        }
    }

    // ===== 受け付ける・受け付けない =====

    @Test
    void 本物の画像は受け付ける() throws Exception {
        ImageStorageService service = new ImageStorageService(uploadDir.toString());
        assertThat(service.isSupported(new MockMultipartFile("image", "a.png", "image/png", image("png", 10, 10)))).isTrue();
        assertThat(service.isSupported(new MockMultipartFile("image", "a.jpg", "image/jpeg", image("jpg", 10, 10)))).isTrue();
        assertThat(service.isSupported(new MockMultipartFile("image", "a.gif", "image/gif", image("gif", 10, 10)))).isTrue();
        assertThat(service.isSupported(new MockMultipartFile("image", "a.webp", "image/webp", webpWithExif()))).isTrue();
    }

    @Test
    void 画像と名乗っていても中身が画像でなければ受け付けない() throws Exception {
        ImageStorageService service = new ImageStorageService(uploadDir.toString());
        byte[] html = "<html><script>alert(1)</script></html>".getBytes();
        assertThat(service.isSupported(new MockMultipartFile("image", "a.jpg", "image/jpeg", html))).isFalse();
        assertThat(service.isSupported(new MockMultipartFile("image", "a.png", "image/png", new byte[0]))).isFalse();
        // PNG の印だけ付いた、壊れたファイル
        byte[] broken = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0};
        assertThat(service.isSupported(new MockMultipartFile("image", "a.png", "image/png", broken))).isFalse();
    }

    @Test
    void 画像以外の種類は受け付けない() throws Exception {
        ImageStorageService service = new ImageStorageService(uploadDir.toString());
        assertThat(service.isSupported(new MockMultipartFile("image", "a.svg", "image/svg+xml", image("png", 10, 10)))).isFalse();
    }

    @Test
    void 拡張子は中身から調べた種類で決める() throws Exception {
        ImageStorageService service = new ImageStorageService(uploadDir.toString());
        // 種類は JPEG と名乗っているが、中身は PNG
        String saved = service.store(new MockMultipartFile("image", "a.jpg", "image/jpeg", image("png", 10, 10)));
        assertThat(saved).endsWith(".png");
        assertThat(uploadDir.resolve(saved)).exists();
    }

    // ===== 写真の情報を消す =====

    @Test
    void JPEGの撮影場所を消し向きを直して保存する() throws Exception {
        byte[] original = withExif(image("jpg", 300, 200));
        // 元の写真には撮影場所が入っている（テストの前提の確認）
        Metadata before = ImageMetadataReader.readMetadata(new ByteArrayInputStream(original));
        assertThat(before.getFirstDirectoryOfType(GpsDirectory.class)).isNotNull();

        ImageStorageService service = new ImageStorageService(uploadDir.toString());
        String saved = service.store(new MockMultipartFile("image", "a.jpg", "image/jpeg", original));
        byte[] stored = Files.readAllBytes(uploadDir.resolve(saved));

        Metadata after = ImageMetadataReader.readMetadata(new ByteArrayInputStream(stored));
        assertThat(after.getFirstDirectoryOfType(GpsDirectory.class)).isNull();      // 撮影場所は消えた
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(stored));
        assertThat(img.getWidth()).isEqualTo(200);                                   // 右に90度回したので、縦横が入れ替わる
        assertThat(img.getHeight()).isEqualTo(300);
    }

    @Test
    void 大きすぎる写真は長い辺を2000に縮める() throws Exception {
        ImageStorageService service = new ImageStorageService(uploadDir.toString());
        String saved = service.store(new MockMultipartFile("image", "a.jpg", "image/jpeg", image("jpg", 3000, 1500)));
        BufferedImage img = ImageIO.read(uploadDir.resolve(saved).toFile());
        assertThat(img.getWidth()).isEqualTo(2000);
        assertThat(img.getHeight()).isEqualTo(1000);
    }

    @Test
    void PNGは透明な部分を残したまま作り直す() throws Exception {
        ImageStorageService service = new ImageStorageService(uploadDir.toString());
        String saved = service.store(new MockMultipartFile("image", "a.png", "image/png", image("png", 20, 10)));
        BufferedImage img = ImageIO.read(uploadDir.resolve(saved).toFile());
        assertThat(img.getColorModel().hasAlpha()).isTrue();
        assertThat(img.getWidth()).isEqualTo(20);
    }

    @Test
    void WebPは写真の情報のかたまりだけを取り除く() throws Exception {
        ImageStorageService service = new ImageStorageService(uploadDir.toString());
        String saved = service.store(new MockMultipartFile("image", "a.webp", "image/webp", webpWithExif()));
        byte[] stored = Files.readAllBytes(uploadDir.resolve(saved));
        String text = new String(stored, StandardCharsets.US_ASCII);

        assertThat(text).doesNotContain("EXIF").doesNotContain("GPS-SECRET");   // 写真の情報は消えた
        assertThat(text).contains("VP8 ");                                      // 画像のかたまりは残る
        assertThat(stored[20] & 0x08).isZero();                                 // VP8X の「EXIF あり」の印も消えた
    }
}
