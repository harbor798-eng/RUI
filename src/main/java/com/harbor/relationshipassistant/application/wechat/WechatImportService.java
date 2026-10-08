package com.harbor.relationshipassistant.application.wechat;

import com.harbor.relationshipassistant.application.config.AppConfigService;
import com.harbor.relationshipassistant.application.relationship.RelationshipService;
import com.harbor.relationshipassistant.common.exception.ImportException;
import com.harbor.relationshipassistant.common.exception.ValidationException;
import com.harbor.relationshipassistant.domain.relationship.Relationship;
import com.harbor.relationshipassistant.domain.relationship.RelationshipStage;
import com.harbor.relationshipassistant.infrastructure.importer.ImportPreview;
import com.harbor.relationshipassistant.infrastructure.importer.ImportRequest;
import com.harbor.relationshipassistant.infrastructure.importer.wechat.SelfWxidCandidates;
import com.harbor.relationshipassistant.infrastructure.importer.wechat.WechatContact;
import com.harbor.relationshipassistant.infrastructure.importer.wechat.WechatContactDiscovery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * 微信导入业务编排层。
 * <p>串联：WChatSJ → 联系人发现 → selfWxid → Relationship → ImportService → chat_message。</p>
 * <p>本类不直接 JDBC、不解析微信消息、不操作 HTTP。</p>
 */
public class WechatImportService {

    private static final Logger log = LoggerFactory.getLogger(WechatImportService.class);

    private final WechatContactDiscovery discovery;
    private final SelfWxidCandidates selfCandidates;
    private final AppConfigService appConfig;
    private final RelationshipService relationshipService;
    private final com.harbor.relationshipassistant.application.importjob.ImportService importService;

    public WechatImportService(WechatContactDiscovery discovery,
                               SelfWxidCandidates selfCandidates,
                               AppConfigService appConfig,
                               RelationshipService relationshipService,
                               com.harbor.relationshipassistant.application.importjob.ImportService importService) {
        this.discovery = discovery;
        this.selfCandidates = selfCandidates;
        this.appConfig = appConfig;
        this.relationshipService = relationshipService;
        this.importService = importService;
    }

    public List<WechatContact> listContacts(Path root) {
        requireRoot(root);
        return discovery.listPersonal(root);
    }

    /** 按微信号 alias 查找联系人；查不到返回 empty。 */
    public Optional<WechatContact> findContactByAlias(Path root, String alias) {
        requireRoot(root);
        return discovery.findByAlias(root, alias);
    }

    public Optional<String> getSelfWxid() {
        return appConfig.getSelfWxid();
    }

    public List<SelfWxidCandidates.Candidate> getSelfWxidCandidates(Path root) {
        requireRoot(root);
        return selfCandidates.list(root);
    }

    public void setSelfWxid(String wxid) {
        appConfig.setSelfWxid(wxid);
    }

    public Optional<String> getLastRoot() { return appConfig.getLastRoot(); }
    public void setLastRoot(String root) { appConfig.setLastRoot(root); }

    /** 导入结果。 */
    public static class ImportResult {
        public final long relationshipId;
        public final long parsed;
        public final long inserted;
        public final long duplicates;
        public final String targetWxid;
        public final String displayName;
        public ImportResult(long relationshipId, long parsed, long inserted, long duplicates,
                            String targetWxid, String displayName) {
            this.relationshipId = relationshipId;
            this.parsed = parsed;
            this.inserted = inserted;
            this.duplicates = duplicates;
            this.targetWxid = targetWxid;
            this.displayName = displayName;
        }
    }

    /**
     * 完整导入流程。
     * <p>时序：selfWxid 校验 → 联系人反查 → preview（不写库）→ Relationship 解析/绑定 → confirm（事务写库）。</p>
     */
    public ImportResult importContact(Path root, String targetWxid) {
        requireRoot(root);
        if (targetWxid == null || targetWxid.isBlank()) {
            throw new ValidationException("targetWxid 不能为空", "WechatImportService.importContact");
        }
        // 1. selfWxid
        String selfWxid = appConfig.getSelfWxid()
                .orElseThrow(() -> new SelfWxidNotConfiguredException("尚未配置 selfWxid，请先在设置中确认"));
        // 2. 反查联系人
        WechatContact contact = discovery.findByWxid(root, targetWxid.trim())
                .orElseThrow(() -> new ImportException("chats.db 中找不到 wxid=" + targetWxid,
                        "WechatImportService.importContact"));
        String displayName = contact.getDisplayName();

        // 3. 构造 ImportRequest 并 preview（preview 不写业务库）
        ImportRequest req = new ImportRequest();
        req.setImporterType("WECHAT_SQLITE");
        req.setSourceLocation(root.toString());
        req.setSelfWxid(selfWxid);
        req.setTargetChatName(displayName);
        ImportPreview preview = importService.preview(req);

        // 4. 解析/创建/绑定 Relationship；记录本次动作以便失败时补偿
        Relationship rel;
        String action; // "REUSED" | "BOUND" | "NEW"
        Optional<Relationship> existing = relationshipService.findByWechatWxid(targetWxid.trim());
        if (existing.isPresent()) {
            rel = existing.get();
            action = "REUSED";
        } else {
            List<Relationship> sameName = relationshipService.findByName(displayName);
            if (sameName.isEmpty()) {
                rel = relationshipService.create(displayName, "我", RelationshipStage.INITIAL_CONTACT, targetWxid.trim());
                action = "NEW";
            } else if (sameName.size() == 1) {
                relationshipService.bindWechatWxid(sameName.get(0).getId(), targetWxid.trim());
                rel = relationshipService.get(sameName.get(0).getId());
                action = "BOUND";
            } else {
                throw new AmbiguousRelationshipNameException("存在多个名为 " + displayName + " 的 Relationship，请手动选择",
                        "WechatImportService.resolveRelationship");
            }
        }

        // 5. confirm（事务写 chat_message）；失败时按动作补偿
        int inserted;
        try {
            inserted = importService.confirm(rel.getId(), preview);
        } catch (RuntimeException e) {
            compensateAfterConfirmFailure(rel.getId(), action);
            throw e;
        }
        long duplicates = preview.getTotalParsed() - inserted;
        // 成功才记忆 root
        appConfig.setLastRoot(root.toString());
        log.info("[WECHAT-IMPORT] rel={} wxid={} parsed={} inserted={} dup={}",
                rel.getId(), targetWxid, preview.getTotalParsed(), inserted, duplicates);
        return new ImportResult(rel.getId(), preview.getTotalParsed(), inserted, duplicates,
                targetWxid.trim(), displayName);
    }

    private void compensateAfterConfirmFailure(long relId, String action) {
        try {
            if ("NEW".equals(action)) {
                // 本次刚创建的空 relationship：软删，避免幽灵记录
                relationshipService.delete(relId);
                log.warn("[WECHAT-IMPORT] compensate: NEW relationship {} soft-deleted after confirm failure", relId);
            } else if ("BOUND".equals(action)) {
                // 本次刚绑到已有 relationship 的 wxid：解绑，恢复原状
                relationshipService.unbindWechatWxid(relId);
                log.warn("[WECHAT-IMPORT] compensate: relationship {} wxid unbound after confirm failure", relId);
            }
            // REUSED：原绑定不动
        } catch (Exception ex) {
            log.error("[WECHAT-IMPORT] compensate failed for rel={} action={}: {}", relId, action, ex.toString());
        }
    }

    private void requireRoot(Path root) {
        if (root == null || !Files.isDirectory(root)) {
            throw new ImportException("WChatSJ 目录不存在: " + root, "WechatImportService.requireRoot");
        }
    }

    /** selfWxid 未配置。 */
    public static class SelfWxidNotConfiguredException extends RuntimeException {
        public SelfWxidNotConfiguredException(String msg) { super(msg); }
    }

    /** 同名 Relationship 多个，无法自动绑定。 */
    public static class AmbiguousRelationshipNameException extends RuntimeException {
        public AmbiguousRelationshipNameException(String msg, String op) { super(msg); }
    }
}
