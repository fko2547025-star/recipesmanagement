package com.example.recipesmanagement;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * アプリが起動できるか（設定や部品のつながりに間違いが無いか）を確かめる。
 * @ActiveProfiles("test")：テスト用の設定（メモリ上の空のデータベース）で起動する
 */
@SpringBootTest
@ActiveProfiles("test")
class RecipesmanagementApplicationTests {

	@Test
	void contextLoads() {
	}

}
