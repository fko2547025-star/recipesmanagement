package com.example.recipesmanagement.common;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;

/**
 * 写真から、撮影場所（位置情報）などの情報を取り除く。
 *
 * スマホの写真には、撮影した場所の緯度・経度などが入っていることがある。そのまま公開すると、
 * 自宅の場所などがわかってしまうので、保存する前に取り除く。
 *
 *   JPEG・PNG：画像を読み直して作り直す（作り直すと、写真の情報はすべて消える）。
 *              スマホの写真は「向き」も写真の情報に入っているので、消す前に、その向きに画像を回しておく。
 *              大きすぎる写真は、長い辺を MAX_SIDE に縮める。
 *   WebP     ：Java の標準の部品では読めないので、作り直さずに、写真の情報の部分（EXIF・XMP）だけを切り取る。
 *   GIF      ：位置情報を入れる決まりが無いので、そのまま保存する。
 */
final class ImageSanitizer {

    // 保存する写真の長い辺の最大（これより大きい写真は縮める）
    static final int MAX_SIDE = 2000;

    // 受け付ける写真の画素数の上限（4000万画素）。小さなファイルで巨大な画像を送り、メモリを使い切らせる攻撃を防ぐ
    static final long MAX_PIXELS = 40_000_000L;

    // JPEG で保存するときの画質（0〜1）
    private static final float JPEG_QUALITY = 0.9f;

    private ImageSanitizer() {
    }

    /**
     * 読み込める画像か（壊れていないか・大きすぎないか）を確かめる。中身を全部は読まず、縦横の大きさだけを見る。
     *
     * @param type ImageStorageService が中身から調べた種類（image/jpeg など）
     */
    static boolean isReadable(byte[] data, String type) {
        if ("image/webp".equals(type)) {
            return stripWebpMetadata(data) != null;   // チャンクの並びが壊れていなければよい
        }
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                return false;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                return pixels > 0 && pixels <= MAX_PIXELS;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /**
     * 写真の情報を取り除いた画像を返す。
     *
     * @throws IllegalArgumentException 読み込めない画像のとき
     */
    static byte[] sanitize(byte[] data, String type) {
        try {
            return switch (type) {
                case "image/jpeg" -> rebuild(data, orientation(data), "jpg");
                case "image/png" -> rebuild(data, 1, "png");
                case "image/webp" -> {
                    byte[] cleaned = stripWebpMetadata(data);
                    if (cleaned == null) {
                        throw new IllegalArgumentException("WebP の画像が壊れています");
                    }
                    yield cleaned;
                }
                default -> data;   // GIF
            };
        } catch (IOException e) {
            throw new IllegalArgumentException("画像を読み込めませんでした", e);
        }
    }

    // ===== JPEG・PNG：作り直す =====

    private static byte[] rebuild(byte[] data, int orientation, String format) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
        if (image == null) {
            throw new IllegalArgumentException("画像を読み込めませんでした");
        }
        image = applyOrientation(image, orientation);
        image = shrink(image);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (format.equals("png")) {
            ImageIO.write(image, "png", out);   // PNG は透明な部分もそのまま
        } else {
            writeJpeg(toRgb(image), out);
        }
        return out.toByteArray();
    }

    /**
     * 写真の情報に入っている「向き」（1〜8）を読む。読めなければ 1（そのまま）。
     * スマホは縦に構えて撮っても、画像は横向きのまま保存し、「90度回して見せて」という情報を付けている。
     */
    private static int orientation(byte[] data) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(data));
            ExifIFD0Directory exif = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (exif != null && exif.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                return exif.getInt(ExifIFD0Directory.TAG_ORIENTATION);
            }
        } catch (Exception e) {
            // 写真の情報が読めないときは、向きを直さずに作り直す
        }
        return 1;
    }

    /**
     * 「向き」のとおりに画像を回す・裏返す。
     * AffineTransform(m00, m10, m01, m11, m02, m12)：元の点 (x, y) を
     *   新しい x = m00*x + m01*y + m02、新しい y = m10*x + m11*y + m12 に移す
     */
    static BufferedImage applyOrientation(BufferedImage image, int orientation) {
        int w = image.getWidth();
        int h = image.getHeight();
        AffineTransform t;
        boolean swap = false;   // 90度・270度回すときは、縦と横が入れ替わる
        switch (orientation) {
            case 2 -> t = new AffineTransform(-1, 0, 0, 1, w, 0);               // 左右反転
            case 3 -> t = new AffineTransform(-1, 0, 0, -1, w, h);              // 180度回転
            case 4 -> t = new AffineTransform(1, 0, 0, -1, 0, h);               // 上下反転
            case 5 -> { t = new AffineTransform(0, 1, 1, 0, 0, 0); swap = true; }    // 左右反転して270度
            case 6 -> { t = new AffineTransform(0, 1, -1, 0, h, 0); swap = true; }   // 右に90度回転
            case 7 -> { t = new AffineTransform(0, -1, -1, 0, h, w); swap = true; }  // 左右反転して90度
            case 8 -> { t = new AffineTransform(0, -1, 1, 0, 0, w); swap = true; }   // 左に90度回転
            default -> {
                return image;   // 1 か、知らない値：そのまま
            }
        }
        BufferedImage rotated = new BufferedImage(swap ? h : w, swap ? w : h, imageType(image));
        Graphics2D g = rotated.createGraphics();
        g.drawImage(image, t, null);
        g.dispose();
        return rotated;
    }

    /** 長い辺が MAX_SIDE より大きければ、縦横の比率を保ったまま縮める */
    static BufferedImage shrink(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        int longSide = Math.max(w, h);
        if (longSide <= MAX_SIDE) {
            return image;
        }
        double scale = (double) MAX_SIDE / longSide;
        int nw = Math.max(1, (int) Math.round(w * scale));
        int nh = Math.max(1, (int) Math.round(h * scale));
        BufferedImage small = new BufferedImage(nw, nh, imageType(image));
        Graphics2D g = small.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(image, 0, 0, nw, nh, null);
        g.dispose();
        return small;
    }

    /** JPEG は透明を扱えないので、白い背景の上に描いた RGB の画像にする */
    private static BufferedImage toRgb(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_RGB) {
            return image;
        }
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return rgb;
    }

    private static void writeJpeg(BufferedImage image, ByteArrayOutputStream out) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(JPEG_QUALITY);
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    private static int imageType(BufferedImage image) {
        return image.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
    }

    // ===== WebP：写真の情報の部分だけ切り取る =====

    /**
     * WebP は「RIFF ＋ 大きさ ＋ WEBP」のあとに、「名前4文字 ＋ 大きさ ＋ 中身」のかたまり（チャンク）が並んでいる。
     * 写真の情報のかたまり（EXIF・XMP）だけを取り除き、VP8X（目次のようなもの）の「EXIF・XMP あり」の印も消す。
     *
     * @return 取り除いた WebP。かたまりの並びが壊れていれば null
     */
    static byte[] stripWebpMetadata(byte[] data) {
        if (data.length < 12) {
            return null;
        }
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        int pos = 12;
        while (pos < data.length) {
            if (pos + 8 > data.length) {
                return null;
            }
            String name = new String(data, pos, 4, StandardCharsets.US_ASCII);
            long size = readLittleEndian(data, pos + 4);
            long end = pos + 8 + size + (size % 2);   // 中身が奇数バイトなら、1バイト詰め物が付く
            if (end > data.length + (size % 2)) {
                return null;
            }
            end = Math.min(end, data.length);
            if (!name.equals("EXIF") && !name.equals("XMP ")) {
                byte[] chunk = Arrays.copyOfRange(data, pos, (int) end);
                if (name.equals("VP8X") && chunk.length > 8) {
                    chunk[8] &= (byte) ~(0x08 | 0x04);   // 「EXIF あり」「XMP あり」の印を消す
                }
                body.writeBytes(chunk);
            }
            pos = (int) end;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("RIFF".getBytes(StandardCharsets.US_ASCII));
        writeLittleEndian(out, body.size() + 4);
        out.writeBytes("WEBP".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(body.toByteArray());
        return out.toByteArray();
    }

    private static long readLittleEndian(byte[] data, int pos) {
        return (data[pos] & 0xFFL) | (data[pos + 1] & 0xFFL) << 8 | (data[pos + 2] & 0xFFL) << 16 | (data[pos + 3] & 0xFFL) << 24;
    }

    private static void writeLittleEndian(ByteArrayOutputStream out, long value) {
        out.write((int) (value & 0xFF));
        out.write((int) (value >> 8 & 0xFF));
        out.write((int) (value >> 16 & 0xFF));
        out.write((int) (value >> 24 & 0xFF));
    }
}
