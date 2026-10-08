package com.harbor.relationshipassistant.application.config;

import com.harbor.relationshipassistant.common.exception.ValidationException;
import com.harbor.relationshipassistant.infrastructure.persistence.AppConfigRepository;

import java.util.Optional;

/**
 * 应用级配置业务入口。V1 仅暴露 selfWxid 读写，不开放通用 KV。
 */
public class AppConfigService {

    private static final String KEY_SELF_WXID = "wechat.self_wxid";
    private static final String KEY_LAST_ROOT = "wechat.last_root";

    private final AppConfigRepository repo;

    public AppConfigService(AppConfigRepository repo) { this.repo = repo; }

    public Optional<String> getSelfWxid() {
        return repo.get(KEY_SELF_WXID);
    }

    public void setSelfWxid(String wxid) {
        if (wxid == null || wxid.trim().isEmpty()) {
            throw new ValidationException("selfWxid 不能为空", "AppConfigService.setSelfWxid");
        }
        repo.put(KEY_SELF_WXID, wxid.trim());
    }

    public Optional<String> getLastRoot() {
        return repo.get(KEY_LAST_ROOT);
    }

    public void setLastRoot(String root) {
        if (root == null || root.trim().isEmpty()) return;
        repo.put(KEY_LAST_ROOT, root.trim());
    }
}
