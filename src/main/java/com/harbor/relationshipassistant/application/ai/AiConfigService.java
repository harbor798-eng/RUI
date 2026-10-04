package com.harbor.relationshipassistant.application.ai;

import com.harbor.relationshipassistant.common.exception.AIException;
import com.harbor.relationshipassistant.infrastructure.ai.AIProvider;
import com.harbor.relationshipassistant.infrastructure.ai.AIRequest;
import com.harbor.relationshipassistant.infrastructure.ai.AIResponse;
import com.harbor.relationshipassistant.infrastructure.ai.DeepSeekProvider;
import com.harbor.relationshipassistant.infrastructure.persistence.DataSourceFactory;
import com.harbor.relationshipassistant.infrastructure.security.AesCryptoService;
import com.harbor.relationshipassistant.infrastructure.importer.wechat.MessageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * AI Provider 配置服务：
 * 配置（provider_name/base_url/model/encrypted_api_key）存 ai_provider_config，
 * API Key 用 AesCryptoService 加密落库；工厂按 provider_name 路由到具体实现。
 * 日志纪律：只记 provider/model/status/耗时，绝不记录 API Key 或 Authorization。
 */
public class AiConfigService {

    private static final Logger log = LoggerFactory.getLogger(AiConfigService.class);

    private final DataSourceFactory ds;
    private final AesCryptoService crypto;

    public AiConfigService(DataSourceFactory ds, AesCryptoService crypto) {
        this.ds = ds;
        this.crypto = crypto;
    }

    /** 当前生效配置（不解密 Key，UI 只显示掩码）。 */
    public record ProviderView(String providerName, String baseUrl, String model,
                               boolean hasKey, String maskedKey) {}

    public Optional<ProviderView> loadView() {
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT provider_name, base_url, model, encrypted_api_key, is_active " +
                             "FROM ai_provider_config WHERE is_active=1 ORDER BY id DESC LIMIT 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    log.info("[AI_CONFIG_LOAD] no active config");
                    return Optional.empty();
                }
                String enc = rs.getString(4);
                String masked = "********";
                if (enc != null && !enc.isBlank()) {
                    String plain = crypto.decrypt(enc);
                    masked = plain.length() <= 4 ? "****"
                            : plain.substring(0, Math.min(3, plain.length() - 4)) + "****" +
                              plain.substring(plain.length() - 4);
                }
                log.info("[AI_CONFIG_LOAD] provider={} model={} hasKey={}", rs.getString(1), rs.getString(3), enc != null);
                return Optional.of(new ProviderView(
                        rs.getString(1), rs.getString(2), rs.getString(3), enc != null, masked));
            }
        } catch (Exception e) {
            throw new AIException("读取 AI 配置失败", "loadView", e);
        }
    }

    /**
     * 保存配置。apiKey 传 null/空表示不修改已有 Key。
     */
    public void save(String providerName, String baseUrl, String model, String apiKey) {
        if (!DeepSeekProvider.NAME.equals(providerName)) {
            throw new AIException("当前阶段仅支持 DeepSeek Provider", "save");
        }
        String url = (baseUrl == null || baseUrl.isBlank()) ? DeepSeekProvider.DEFAULT_BASE_URL : baseUrl.trim();
        String mdl = (model == null || model.isBlank()) ? DeepSeekProvider.DEFAULT_MODEL : model.trim();
        LocalDateTime now = LocalDateTime.now(MessageMapper.WECHAT_ZONE);
        boolean keyProvided;
        try (Connection c = ds.newConnection()) {
            c.setAutoCommit(false);
            try {
                // 全部置为非 active，再 upsert 当前行
                try (PreparedStatement ps = c.prepareStatement("UPDATE ai_provider_config SET is_active=0")) {
                    ps.executeUpdate();
                }
                Long existingId;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT id FROM ai_provider_config WHERE provider_name=? ORDER BY id DESC LIMIT 1")) {
                    ps.setString(1, providerName);
                    try (ResultSet rs = ps.executeQuery()) {
                        existingId = rs.next() ? rs.getLong(1) : null;
                    }
                }
                keyProvided = apiKey != null && !apiKey.isBlank();
                if (existingId == null) {
                    if (!keyProvided) throw new AIException("首次保存必须填写 API Key", "save");
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO ai_provider_config(provider_name, base_url, model, encrypted_api_key, is_active, created_at, updated_at) " +
                                    "VALUES(?,?,?,?,1,?,?)")) {
                        ps.setString(1, providerName);
                        ps.setString(2, url);
                        ps.setString(3, mdl);
                        ps.setString(4, crypto.encrypt(apiKey.trim()));
                        ps.setObject(5, now);
                        ps.setObject(6, now);
                        ps.executeUpdate();
                    }
                } else {
                    if (keyProvided) {
                        try (PreparedStatement ps = c.prepareStatement(
                                "UPDATE ai_provider_config SET base_url=?, model=?, encrypted_api_key=?, is_active=1, updated_at=? WHERE id=?")) {
                            ps.setString(1, url); ps.setString(2, mdl);
                            ps.setString(3, crypto.encrypt(apiKey.trim()));
                            ps.setObject(4, now); ps.setLong(5, existingId);
                            ps.executeUpdate();
                        }
                    } else {
                        try (PreparedStatement ps = c.prepareStatement(
                                "UPDATE ai_provider_config SET base_url=?, model=?, is_active=1, updated_at=? WHERE id=?")) {
                            ps.setString(1, url); ps.setString(2, mdl);
                            ps.setObject(3, now); ps.setLong(4, existingId);
                            ps.executeUpdate();
                        }
                    }
                }
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        } catch (Exception e) {
            log.error("[AI_CONFIG_SAVE] provider={} failed", providerName);
            throw new AIException("保存 AI 配置失败", "save", e);
        }
        log.info("[AI_CONFIG_SAVE] provider={} model={} keyUpdated={} result=SUCCESS", providerName, mdl, keyProvided);
    }

    /** 按当前 active 配置构造可用 Provider；配置缺失抛明确异常。 */
    public AIProvider buildProvider() {
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT provider_name, base_url, model, encrypted_api_key FROM ai_provider_config " +
                             "WHERE is_active=1 ORDER BY id DESC LIMIT 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new AIException("请先配置 DeepSeek API Key", "buildProvider");
                }
                String provider = rs.getString(1);
                String apiKey = crypto.decrypt(rs.getString(4));
                if (apiKey == null || apiKey.isBlank()) {
                    throw new AIException("请先配置 DeepSeek API Key", "buildProvider");
                }
                log.info("[AI_PROVIDER_INIT] provider={} model={}", provider, rs.getString(3));
                if (DeepSeekProvider.NAME.equals(provider)) {
                    return new DeepSeekProvider(rs.getString(2), apiKey, rs.getString(3));
                }
                throw new AIException("暂不支持的 Provider: " + provider, "buildProvider");
            }
        } catch (AIException e) {
            throw e;
        } catch (Exception e) {
            throw new AIException("初始化 AI Provider 失败", "buildProvider", e);
        }
    }

    /** 测试连接：最小请求，返回 AI 文本；失败抛 AIException（消息友好，不含 Key）。 */
    public String testConnection() {
        AIProvider provider = buildProvider();
        AIRequest req = AIRequest.of(
                "You are a helpful assistant.",
                List.of(new AIRequest.Turn("user", "请回复：AI连接测试成功")));
        req.setTemperature(0.0);
        req.setMaxTokens(50);
        AIResponse resp = provider.generate(req);
        log.info("[DEEPSEEK_REQUEST] test connection ok, respLen={}", resp.text() == null ? 0 : resp.text().length());
        return resp.text();
    }
}
