package com.harbor.relationshipassistant.common.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * 配置读取。优先级（从低到高）：
 * <ol>
 *   <li>classpath:application.properties（仓库内，不含真实密码）</li>
 *   <li>外部文件 application-local.properties（工作目录或 ./config/，本地自填，不提交）</li>
 *   <li>环境变量 RA_DB_URL / RA_DB_USERNAME / RA_DB_PASSWORD（最高优先级）</li>
 * </ol>
 * 敏感项（密码/API Key）不打印、不写日志。
 */
public class AppConfig {

    private final Properties props = new Properties();

    public AppConfig() {
        try (InputStream in = AppConfig.class.getResourceAsStream("/application.properties")) {
            if (in != null) props.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("无法加载 application.properties", e);
        }
        // 外部本地覆盖文件（可选）
        for (String p : new String[] {"application-local.properties",
                "config/application-local.properties"}) {
            Path f = Path.of(p);
            if (Files.isReadable(f)) {
                try (InputStream in = Files.newInputStream(f)) {
                    props.load(in);
                } catch (IOException e) {
                    throw new IllegalStateException("无法加载 " + p, e);
                }
            }
        }
        // 环境变量覆盖（不把值落盘）
        overrideFromEnv("RA_DB_URL", "db.url");
        overrideFromEnv("RA_DB_USERNAME", "db.username");
        overrideFromEnv("RA_DB_PASSWORD", "db.password");
    }

    private void overrideFromEnv(String env, String key) {
        String v = System.getenv(env);
        if (v != null && !v.isBlank()) props.setProperty(key, v);
    }

    public String get(String key) { return props.getProperty(key); }

    public String dbUrl() { return props.getProperty("db.url"); }
    public String dbUsername() { return props.getProperty("db.username"); }
    public String dbPassword() { return props.getProperty("db.password", ""); }

    public String aiProviderName() { return props.getProperty("ai.provider.name", "openai-compatible"); }
    public String aiBaseUrl() { return props.getProperty("ai.provider.base-url"); }
    public String aiModel() { return props.getProperty("ai.provider.model"); }
    public String aiApiKey() { return props.getProperty("ai.provider.api-key", ""); }

    public String aesKey() { return props.getProperty("security.aes-key"); }
}
