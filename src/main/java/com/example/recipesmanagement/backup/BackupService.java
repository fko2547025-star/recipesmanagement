package com.example.recipesmanagement.backup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * データベースの控え（バックアップ）を、毎日決まった時刻に zip ファイルで作る。
 *
 * H2 の「BACKUP TO 'ファイル名'」という SQL を使う。アプリを動かしたまま、その時点のデータを丸ごと保存できる。
 * 古い控えは keepDays 日を過ぎたら消す。
 *
 * 元に戻すとき：アプリを止めて、zip を開いた中の .mv.db ファイルを、データベースのファイルと入れ替える。
 *
 * 設定（application.properties）：
 *   app.backup.dir       保存先のフォルダ。空なら控えを作らない
 *   app.backup.cron      作る時刻（既定は毎日 3:00）
 *   app.backup.keep-days 何日分残すか（既定は7日）
 */
@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);
    private static final DateTimeFormatter NAME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final JdbcTemplate jdbc;
    private final String dir;
    private final int keepDays;

    public BackupService(DataSource dataSource,
                         @Value("${app.backup.dir:}") String dir,
                         @Value("${app.backup.keep-days:7}") int keepDays) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.dir = dir;
        this.keepDays = keepDays;
    }

    /** 毎日決まった時刻に動く（RecipesManagementApplication の @EnableScheduling で有効になる） */
    @Scheduled(cron = "${app.backup.cron:0 0 3 * * *}")
    public void scheduledBackup() {
        if (dir == null || dir.isBlank()) {
            return;   // 保存先が決まっていなければ何もしない
        }
        try {
            Path file = backup();
            log.info("データベースの控えを作りました：{}", file);
        } catch (Exception e) {
            log.error("データベースの控えを作れませんでした", e);
        }
    }

    /**
     * 今すぐ控えを作り、古い控えを消す。
     *
     * @return 作った zip ファイル
     */
    public Path backup() throws IOException {
        Path folder = Path.of(dir);
        Files.createDirectories(folder);
        Path file = folder.resolve("recipes-" + LocalDateTime.now().format(NAME) + ".zip").toAbsolutePath();
        // ファイル名の中の ' は '' にする（SQL の文字列の決まり）
        jdbc.execute("BACKUP TO '" + file.toString().replace("'", "''") + "'");
        removeOld(folder);
        return file;
    }

    /** keepDays 日より古い控え（recipes-〇〇.zip）を消す */
    private void removeOld(Path folder) throws IOException {
        Instant limit = Instant.now().minus(Duration.ofDays(keepDays));
        try (Stream<Path> files = Files.list(folder)) {
            for (Path f : (Iterable<Path>) files::iterator) {
                String name = f.getFileName().toString();
                if (name.startsWith("recipes-") && name.endsWith(".zip")
                        && Files.getLastModifiedTime(f).toInstant().isBefore(limit)) {
                    Files.deleteIfExists(f);
                }
            }
        }
    }
}
