package com.example.recipesmanagement.backup;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.zip.ZipFile;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * データベースの控え（BackupService）のテスト。一時フォルダに H2 のデータベースを作って試す。
 */
class BackupServiceTest {

    @TempDir
    Path temp;

    @Test
    void zipの控えを作り古い控えは消す() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:" + temp.resolve("db/test").toAbsolutePath());
        dataSource.setUser("sa");
        new JdbcTemplate(dataSource).execute("CREATE TABLE t (id INT)");

        Path backups = temp.resolve("backups");
        Files.createDirectories(backups);
        // 8日前の控え（消える）と、ほかのファイル（消えない）
        Path old = Files.writeString(backups.resolve("recipes-20000101-030000.zip"), "old");
        Files.setLastModifiedTime(old, FileTime.from(Instant.now().minus(Duration.ofDays(8))));
        Path other = Files.writeString(backups.resolve("memo.txt"), "keep");
        Files.setLastModifiedTime(other, FileTime.from(Instant.now().minus(Duration.ofDays(30))));

        Path file = new BackupService(dataSource, backups.toString(), 7).backup();

        assertThat(file).exists();
        try (ZipFile zip = new ZipFile(file.toFile())) {
            assertThat(zip.stream().anyMatch(e -> e.getName().endsWith(".mv.db"))).isTrue();
        }
        assertThat(old).doesNotExist();
        assertThat(other).exists();
    }

    @Test
    void 保存先が空なら何もしない() {
        new BackupService(new JdbcDataSource(), "", 7).scheduledBackup();   // 例外にならない
    }
}
