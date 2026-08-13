package com.skillhub.config;

import com.skillhub.repo.sqlite.SqliteSchema;
import com.skillhub.service.DemoDataSeeder;
import com.skillhub.service.SeniorReader;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

/**
 * 显式按顺序：(1) 建表 (2) 扫描 seniors 目录。
 * 通过 @PostConstruct 在依赖注入完成后立刻跑，
 * 不依赖 Spring Boot 的启动事件顺序，保证 ChatOrchestrator 注入前 DB 已可用。
 */
@Configuration
public class InitRunner {

    private final SqliteSchema schema;
    private final SeniorReader reader;
    private final DemoDataSeeder demoData;

    public InitRunner(SqliteSchema schema, SeniorReader reader, DemoDataSeeder demoData) {
        this.schema = schema;
        this.reader = reader;
        this.demoData = demoData;
    }

    @PostConstruct
    public void init() {
        schema.init();
        reader.scanOnBoot();
        demoData.seed();
    }
}
