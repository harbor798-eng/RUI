package com.harbor.relationshipassistant.application.profile;

import com.harbor.relationshipassistant.common.exception.DatabaseException;
import com.harbor.relationshipassistant.domain.profile.ItemSourceType;
import com.harbor.relationshipassistant.domain.profile.OwnerType;
import com.harbor.relationshipassistant.domain.profile.UsageWeight;
import com.harbor.relationshipassistant.infrastructure.importer.wechat.MessageMapper;
import com.harbor.relationshipassistant.infrastructure.persistence.AuditLogRepository;
import com.harbor.relationshipassistant.infrastructure.persistence.DataSourceFactory;
import com.harbor.relationshipassistant.infrastructure.security.AesCryptoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 档案业务（本阶段只做用户手动维护：source=USER）。
 *
 * <p>表结构（V1 已有，未改）：
 * profile（关系下 ME/OTHER 各一条主记录）→ profile_item（分类键值条目）；
 * profile_nickname_history（对方历史昵称）；private_profile_data（私密字段 AES 加密）。</p>
 *
 * <p>时间口径：与聊天消息一致，LocalDateTime + Asia/Shanghai，setObject 直写，不用 Timestamp。</p>
 *
 * <p>日志纪律：只记 relationshipId / owner / key / 结果，绝不打印 value（尤其私密字段）。</p>
 */
public class ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    private final DataSourceFactory ds;
    private final AuditLogRepository audit;
    private final AesCryptoService crypto;

    public ProfileService(DataSourceFactory ds, AuditLogRepository audit, AesCryptoService crypto) {
        this.ds = ds;
        this.audit = audit;
        this.crypto = crypto;
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(MessageMapper.WECHAT_ZONE);
    }

    /** 确保某关系下存在 ME / OTHER 两条 profile 主记录。 */
    public void ensureProfiles(Long relationshipId) {
        try (Connection c = ds.newConnection()) {
            for (OwnerType owner : OwnerType.values()) {
                long profileId;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT id FROM profile WHERE relationship_id=? AND owner_type=?")) {
                    ps.setLong(1, relationshipId);
                    ps.setString(2, owner.name());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) continue;
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO profile(relationship_id, owner_type, created_at, updated_at) VALUES(?,?,?,?)")) {
                    ps.setLong(1, relationshipId);
                    ps.setString(2, owner.name());
                    ps.setObject(3, now());
                    ps.setObject(4, now());
                    ps.executeUpdate();
                }
                log.info("[PROFILE_RELATIONSHIP] relationshipId={} ensure profile owner={}", relationshipId, owner);
            }
        } catch (Exception e) {
            log.error("[PROFILE_ERROR] relationshipId={} ensureProfiles: {}", relationshipId, e.getMessage());
            throw new DatabaseException("初始化档案失败", "ensureProfiles", e);
        }
    }

    // ---------------- 普通条目（profile_item） ----------------

    private Map<String, Map<String, String>> loadGroupedByProfileId(long profileId) {
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT category, item_key, item_value FROM profile_item WHERE profile_id=? AND status='ACTIVE'")) {
            ps.setLong(1, profileId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.computeIfAbsent(rs.getString(1), k -> new LinkedHashMap<>()).put(rs.getString(2), rs.getString(3));
                }
            }
        } catch (Exception e) {
            throw new DatabaseException("读取档案失败", "loadGroupedByProfileId", e);
        }
        return out;
    }

    private Long findProfileId(Long relationshipId, OwnerType owner) {
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id FROM profile WHERE relationship_id <=> ? AND owner_type=?")) {
            if (relationshipId == null) ps.setNull(1, java.sql.Types.BIGINT);
            else ps.setLong(1, relationshipId);
            ps.setString(2, owner.name());
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getLong(1) : null; }
        } catch (Exception e) {
            throw new DatabaseException("查询 profile 失败", "findProfileId", e);
        }
    }

    private long ensureGlobalMeProfileId() {
        Long id = findProfileId(null, OwnerType.ME);
        if (id != null) return id;
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO profile(relationship_id, owner_type, created_at, updated_at) VALUES(NULL, ?, ?, ?)")) {
            ps.setString(1, OwnerType.ME.name());
            ps.setObject(2, now()); ps.setObject(3, now());
            ps.executeUpdate();
            log.info("[PROFILE_GLOBAL] created global ME profile");
            return findProfileId(null, OwnerType.ME);
        } catch (Exception e) {
            throw new DatabaseException("初始化全局档案失败", "ensureGlobalMeProfileId", e);
        }
    }

    public Map<String, Map<String, String>> loadItemsGrouped(Long relationshipId, OwnerType owner) {
        ensureProfiles(relationshipId);
        if (owner == OwnerType.ME) {
            long globalPid = ensureGlobalMeProfileId();
            Map<String, Map<String, String>> merged = loadGroupedByProfileId(globalPid);
            Long relPid = findProfileId(relationshipId, OwnerType.ME);
            if (relPid != null) {
                for (var e : loadGroupedByProfileId(relPid).entrySet()) {
                    merged.computeIfAbsent(e.getKey(), k -> new LinkedHashMap<>()).putAll(e.getValue());
                }
            }
            log.info("[PROFILE_LOAD] relId={} ME merged categories={} (global+relation override)", relationshipId, merged.size());
            return merged;
        }
        return loadGroupedByProfileId(findProfileId(relationshipId, owner));
    }

    public Map<String, String> loadItems(Long relationshipId, OwnerType owner) {
        ensureProfiles(relationshipId);
        Map<String, String> out = new LinkedHashMap<>();
        if (owner == OwnerType.ME) {
            out.putAll(loadFlatByProfileId(ensureGlobalMeProfileId()));
            Long relPid = findProfileId(relationshipId, OwnerType.ME);
            if (relPid != null) out.putAll(loadFlatByProfileId(relPid));
        } else {
            Long pid = findProfileId(relationshipId, owner);
            if (pid != null) out.putAll(loadFlatByProfileId(pid));
        }
        log.info("[PROFILE_LOAD] relationshipId={} profileType={} items={}", relationshipId, owner, out.size());
        return out;
    }

    private Map<String, String> loadFlatByProfileId(long profileId) {
        Map<String, String> out = new LinkedHashMap<>();
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT item_key, item_value FROM profile_item WHERE profile_id=? AND status='ACTIVE'")) {
            ps.setLong(1, profileId);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) out.put(rs.getString(1), rs.getString(2)); }
        } catch (Exception e) {
            throw new DatabaseException("读取档案失败", "loadFlatByProfileId", e);
        }
        return out;
    }

    /** 保存一条字段（category/key/value）：存在则 UPDATE，不存在则 INSERT；空值删除该条目。 */
    public void saveItem(Long relationshipId, OwnerType owner, String category, String key, String value) {
        ensureProfiles(relationshipId);
        boolean blank = value == null || value.isBlank();
        try (Connection c = ds.newConnection()) {
            c.setAutoCommit(false);
            try {
                if (blank) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE profile_item pi JOIN profile p ON pi.profile_id=p.id " +
                                    "SET pi.status='ARCHIVED', pi.updated_at=? " +
                                    "WHERE p.relationship_id=? AND p.owner_type=? AND pi.item_key=? AND pi.status='ACTIVE'")) {
                        ps.setObject(1, now()); ps.setLong(2, relationshipId);
                        ps.setString(3, owner.name()); ps.setString(4, key);
                        ps.executeUpdate();
                    }
                } else {
                    int updated;
                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE profile_item pi JOIN profile p ON pi.profile_id=p.id " +
                                    "SET pi.item_value=?, pi.source_type=?, pi.updated_at=? " +
                                    "WHERE p.relationship_id=? AND p.owner_type=? AND pi.item_key=? AND pi.status='ACTIVE'")) {
                        ps.setString(1, value.trim());
                        ps.setString(2, ItemSourceType.USER.name());
                        ps.setObject(3, now());
                        ps.setLong(4, relationshipId);
                        ps.setString(5, owner.name());
                        ps.setString(6, key);
                        updated = ps.executeUpdate();
                    }
                    if (updated == 0) {
                        try (PreparedStatement ps = c.prepareStatement(
                                "INSERT INTO profile_item(profile_id, category, item_key, item_value, source_type, " +
                                        "usage_weight, status, created_at, updated_at) " +
                                        "SELECT id,?,?,?,?,?,'ACTIVE',?,? FROM profile WHERE relationship_id=? AND owner_type=?")) {
                            ps.setString(1, category);
                            ps.setString(2, key);
                            ps.setString(3, value.trim());
                            ps.setString(4, ItemSourceType.USER.name());
                            ps.setString(5, UsageWeight.NORMAL.name());
                            ps.setObject(6, now());
                            ps.setObject(7, now());
                            ps.setLong(8, relationshipId);
                            ps.setString(9, owner.name());
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
            log.error("[PROFILE_ERROR] relationshipId={} saveItem owner={} key={}: {}",
                    relationshipId, owner, key, e.getMessage());
            throw new DatabaseException("保存档案失败", "saveItem", e);
        }
        audit.log(relationshipId, "PROFILE_SAVE", owner + "/" + category + "/" + key);
        log.info("[PROFILE_UPDATE] relationshipId={} profileType={} key={} blank={}", relationshipId, owner, key, blank);
    }

    // ---------------- 历史昵称（仅对方） ----------------

    public List<NicknameRow> loadNicknames(Long relationshipId) {
        List<NicknameRow> out = new ArrayList<>();
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, nickname, started_at, ended_at FROM profile_nickname_history " +
                             "WHERE relationship_id=? ORDER BY started_at ASC")) {
            ps.setLong(1, relationshipId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new NicknameRow(rs.getLong(1), rs.getString(2),
                            rs.getObject(3, LocalDateTime.class), rs.getObject(4, LocalDateTime.class)));
                }
            }
        } catch (Exception e) {
            log.error("[PROFILE_ERROR] relationshipId={} loadNicknames: {}", relationshipId, e.getMessage());
            throw new DatabaseException("读取历史昵称失败", "loadNicknames", e);
        }
        log.info("[PROFILE_LOAD] relationshipId={} nicknames={}", relationshipId, out.size());
        return out;
    }

    public void addNickname(Long relationshipId, String nickname, LocalDateTime startedAt, LocalDateTime endedAt) {
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO profile_nickname_history(relationship_id, nickname, started_at, ended_at, created_at) " +
                             "VALUES(?,?,?,?,?)")) {
            ps.setLong(1, relationshipId);
            ps.setString(2, nickname);
            ps.setObject(3, startedAt);
            ps.setObject(4, endedAt);
            ps.setObject(5, now());
            ps.executeUpdate();
        } catch (Exception e) {
            log.error("[PROFILE_ERROR] relationshipId={} addNickname: {}", relationshipId, e.getMessage());
            throw new DatabaseException("保存历史昵称失败", "addNickname", e);
        }
        log.info("[PROFILE_SAVE] relationshipId={} nickname added result=SUCCESS", relationshipId);
    }

    // ---------------- 私密字段（AES 加密，不打日志值） ----------------

    /** 读取私密字段：dataType -> 明文。 */
    public Map<String, String> loadPrivate(Long relationshipId, OwnerType owner) {
        ensureProfiles(relationshipId);
        Map<String, String> out = new LinkedHashMap<>();
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT data_type, encrypted_value FROM private_profile_data " +
                             "WHERE relationship_id=? AND owner_type=?")) {
            ps.setLong(1, relationshipId);
            ps.setString(2, owner.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.put(rs.getString(1), crypto.decrypt(rs.getString(2)));
            }
        } catch (Exception e) {
            log.error("[PROFILE_ERROR] relationshipId={} loadPrivate owner={}: {}", relationshipId, owner, e.getMessage());
            throw new DatabaseException("读取私密信息失败", "loadPrivate", e);
        }
        log.info("[PROFILE_LOAD] relationshipId={} profileType={} privateFields={}", relationshipId, owner, out.size());
        return out;
    }

    /** 保存私密字段：upsert 加密值；空值删除该行。ai_visible=0（默认不进 AI 上下文）。 */
    public void savePrivate(Long relationshipId, OwnerType owner, String dataType, String plainValue) {
        ensureProfiles(relationshipId);
        boolean blank = plainValue == null || plainValue.isBlank();
        try (Connection c = ds.newConnection()) {
            if (blank) {
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM private_profile_data WHERE relationship_id=? AND owner_type=? AND data_type=?")) {
                    ps.setLong(1, relationshipId); ps.setString(2, owner.name()); ps.setString(3, dataType);
                    ps.executeUpdate();
                }
            } else {
                int updated;
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE private_profile_data SET encrypted_value=? WHERE relationship_id=? AND owner_type=? AND data_type=?")) {
                    ps.setString(1, crypto.encrypt(plainValue.trim()));
                    ps.setLong(2, relationshipId); ps.setString(3, owner.name()); ps.setString(4, dataType);
                    updated = ps.executeUpdate();
                }
                if (updated == 0) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO private_profile_data(relationship_id, owner_type, data_type, encrypted_value, ai_visible, created_at) " +
                                    "VALUES(?,?,?,?,0,?)")) {
                        ps.setLong(1, relationshipId);
                        ps.setString(2, owner.name());
                        ps.setString(3, dataType);
                        ps.setString(4, crypto.encrypt(plainValue.trim()));
                        ps.setObject(5, now());
                        ps.executeUpdate();
                    }
                }
            }
        } catch (Exception e) {
            log.error("[PROFILE_ERROR] relationshipId={} savePrivate owner={} dataType={}: {}",
                    relationshipId, owner, dataType, e.getMessage());
            throw new DatabaseException("保存私密信息失败", "savePrivate", e);
        }
        log.info("[PROFILE_SAVE] relationshipId={} profileType={} dataType={} blank={} result=SUCCESS",
                relationshipId, owner, dataType, blank);
    }

    /** 历史昵称行。 */
    public record NicknameRow(long id, String nickname, LocalDateTime startedAt, LocalDateTime endedAt) {}

    // ---------------- 全局默认 ME（relationship_id IS NULL） ----------------

    /** 只读 Global ME 的条目（不合并关系专属）。 */
    public Map<String, String> loadGlobalMeItems() {
        long pid = ensureGlobalMeProfileId();
        return loadFlatByProfileId(pid);
    }

    /** 只读当前关系 ME 的条目（不合并全局）。 */
    public Map<String, String> loadRelationMeItems(Long relationshipId) {
        ensureProfiles(relationshipId);
        Long pid = findProfileId(relationshipId, OwnerType.ME);
        return pid == null ? new LinkedHashMap<>() : loadFlatByProfileId(pid);
    }

    /** 保存 Global ME 条目；空值归档。 */
    public void saveGlobalMeItem(String category, String key, String value) {
        long pid = ensureGlobalMeProfileId();
        boolean blank = value == null || value.isBlank();
        try (Connection c = ds.newConnection()) {
            c.setAutoCommit(false);
            try {
                if (blank) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE profile_item SET status='ARCHIVED', updated_at=? WHERE profile_id=? AND item_key=? AND status='ACTIVE'")) {
                        ps.setObject(1, now()); ps.setLong(2, pid); ps.setString(3, key);
                        ps.executeUpdate();
                    }
                } else {
                    int updated;
                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE profile_item SET item_value=?, source_type=?, updated_at=? WHERE profile_id=? AND item_key=? AND status='ACTIVE'")) {
                        ps.setString(1, value.trim());
                        ps.setString(2, ItemSourceType.USER.name());
                        ps.setObject(3, now()); ps.setLong(4, pid); ps.setString(5, key);
                        updated = ps.executeUpdate();
                    }
                    if (updated == 0) {
                        try (PreparedStatement ps = c.prepareStatement(
                                "INSERT INTO profile_item(profile_id, category, item_key, item_value, source_type, usage_weight, status, created_at, updated_at) VALUES(?,?,?,?,?,?,?,?,?)")) {
                            ps.setLong(1, pid); ps.setString(2, category); ps.setString(3, key);
                            ps.setString(4, value.trim()); ps.setString(5, ItemSourceType.USER.name());
                            ps.setString(6, UsageWeight.NORMAL.name()); ps.setString(7, "ACTIVE");
                            ps.setObject(8, now()); ps.setObject(9, now());
                            ps.executeUpdate();
                        }
                    }
                }
                c.commit();
            } catch (Exception e) { c.rollback(); throw e; }
        } catch (Exception e) {
            log.error("[PROFILE_ERROR] saveGlobalMeItem key={}: {}", key, e.getMessage());
            throw new DatabaseException("保存全局默认资料失败", "saveGlobalMeItem", e);
        }
        log.info("[PROFILE_GLOBAL] save key={} blank={}", key, blank);
    }

    // ---------------- AI 可见权限（基于 usage_weight） ----------------

    /** 返回某 owner 下所有 ACTIVE 条目的 key→weight 映射（ME 合并 global+relation）。 */
    public Map<String, String> loadItemWeights(Long relationshipId, OwnerType owner) {
        ensureProfiles(relationshipId);
        Map<String, String> out = new LinkedHashMap<>();
        if (owner == OwnerType.ME) {
            out.putAll(loadWeightsByProfileId(ensureGlobalMeProfileId()));
            Long relPid = findProfileId(relationshipId, OwnerType.ME);
            if (relPid != null) out.putAll(loadWeightsByProfileId(relPid));
        } else {
            Long pid = findProfileId(relationshipId, owner);
            if (pid != null) out.putAll(loadWeightsByProfileId(pid));
        }
        return out;
    }

    private Map<String, String> loadWeightsByProfileId(long profileId) {
        Map<String, String> out = new LinkedHashMap<>();
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT item_key, usage_weight FROM profile_item WHERE profile_id=? AND status='ACTIVE'")) {
            ps.setLong(1, profileId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.put(rs.getString(1), rs.getString(2));
            }
        } catch (Exception e) {
            throw new DatabaseException("读取权重失败", "loadWeightsByProfileId", e);
        }
        return out;
    }

    public Map<String, Boolean> loadAiVisibility(Long relationshipId, OwnerType owner) {
        ensureProfiles(relationshipId);
        Map<String, Boolean> out = new LinkedHashMap<>();
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT pi.item_key, pi.usage_weight FROM profile_item pi " +
                             "JOIN profile p ON pi.profile_id=p.id " +
                             "WHERE p.relationship_id=? AND p.owner_type=? AND pi.status='ACTIVE'")) {
            ps.setLong(1, relationshipId);
            ps.setString(2, owner.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getString(1), !"NONE".equals(rs.getString(2)));
                }
            }
        } catch (Exception e) {
            throw new DatabaseException("读取AI可见权限失败", "loadAiVisibility", e);
        }
        return out;
    }

    public void setAiVisible(Long relationshipId, OwnerType owner, String key, boolean visible) {
        ensureProfiles(relationshipId);
        String weight = visible ? "NORMAL" : "NONE";
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE profile_item pi JOIN profile p ON pi.profile_id=p.id " +
                             "SET pi.usage_weight=?, pi.updated_at=? " +
                             "WHERE p.relationship_id=? AND p.owner_type=? AND pi.item_key=? AND pi.status='ACTIVE'")) {
            ps.setString(1, weight);
            ps.setObject(2, now());
            ps.setLong(3, relationshipId);
            ps.setString(4, owner.name());
            ps.setString(5, key);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new DatabaseException("更新AI可见权限失败", "setAiVisible", e);
        }
        log.info("[PROFILE_PERMISSION] relationshipId={} owner={} key={} visible={}", relationshipId, owner, key, visible);
    }
}
