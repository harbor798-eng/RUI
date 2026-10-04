package com.harbor.relationshipassistant.infrastructure.importer.wechat;

import com.github.luben.zstd.ZstdInputStream;
import com.harbor.relationshipassistant.common.exception.ImportException;
import com.harbor.relationshipassistant.domain.chat.MessageType;
import com.harbor.relationshipassistant.domain.chat.SenderType;
import com.harbor.relationshipassistant.infrastructure.importer.ImportedRawMessage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.regex.Pattern;

/**
 * WechatRawMessage -> ImportedRawMessage 的映射（纯函数，不碰数据库）。
 *
 * <p>local_type 含义只采信真实样本已核验的部分：
 * 1=文本、3=图片XML、47=表情XML、10000=系统消息；其余码值统一归 OTHER，
 * 媒体正式留待后续阶段（见 WechatMediaHandler 扩展点）。</p>
 *
 * <p><b>时间规则（硬约定）</b>：微信 create_time 是 Unix epoch（UTC 绝对秒）。
 * 本系统一律按 {@link #WECHAT_ZONE} = Asia/Shanghai (UTC+8) 转成 LocalDateTime，
 * 表示"微信聊天界面看到的本地时间"；绝不使用 ZoneId.systemDefault()，
 * JVM/电脑在东京时区不得影响微信历史消息时间。</p>
 */
public class MessageMapper {

    /** 微信聊天时间固定为中国标准时间，与 JVM 所在时区无关。 */
    public static final ZoneId WECHAT_ZONE = ZoneId.of("Asia/Shanghai");

    private static final Pattern GROUP_PREFIX = Pattern.compile("^wxid_[a-z0-9_]+:\\s*\\n");

    /** 映射结果，附带原始内容形态统计信息。 */
    public static class Mapped {
        public ImportedRawMessage message;
        public String rawKind;       // plaintext / zstd / null
        public boolean decompressFailed;
    }

    public Mapped map(WechatRawMessage raw, String selfWxid, String targetWxid) {
        Mapped r = new Mapped();
        ImportedRawMessage m = new ImportedRawMessage();

        // sender 方向；系统消息（local_type=10000，如撤回通知）一律 SYSTEM，不参与 ME/OTHER
        String sender = raw.getSenderWxid() == null ? "" : raw.getSenderWxid();
        if (raw.getLocalType() == 10000) {
            m.setSenderType(SenderType.SYSTEM);
        } else {
            m.setSenderType(sender.equals(selfWxid) ? SenderType.ME : SenderType.OTHER);
        }

        // 时间：epoch(UTC) -> 固定 Asia/Shanghai 墙钟时间，与 JVM 时区无关
        m.setRawEpochSeconds(raw.getCreateTime());
        m.setMessageTime(LocalDateTime.ofInstant(
                Instant.ofEpochSecond(raw.getCreateTime()), WECHAT_ZONE));

        // source_message_id：分片表 hash + localId，全库唯一
        String hash = raw.getTableName().substring("Msg_".length());
        m.setSourceMessageId(hash + ":" + raw.getLocalId());

        // 内容：明文 or zstd 解压
        String text;
        if (raw.getRawContent() instanceof byte[]) {
            r.rawKind = "zstd";
            try (ZstdInputStream zis = new ZstdInputStream(
                    new ByteArrayInputStream((byte[]) raw.getRawContent()))) {
                text = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                r.decompressFailed = true;
                text = "[zstd解压失败]";
            }
        } else if (raw.getRawContent() instanceof String s) {
            r.rawKind = "plaintext";
            text = s;
        } else {
            r.rawKind = "empty";
            text = "";
        }
        text = stripGroupPrefix(text).trim();

        // local_type -> MessageType
        m.setMessageType(mapType(raw.getLocalType()));
        m.setContent(contentFor(raw.getLocalType(), text, m.getMessageType()));

        m.setSourceHash(sha256(m.getMessageTime() + "|" + m.getSenderType() + "|" + m.getContent()));
        r.message = m;
        return r;
    }

    private MessageType mapType(long localType) {
        if (localType == 1) return MessageType.TEXT;
        if (localType == 3) return MessageType.IMAGE;
        if (localType == 47) return MessageType.EMOJI;
        if (localType == 10000) return MessageType.SYSTEM;
        return MessageType.OTHER;
    }

    /** 非媒体阶段：媒体类型只放占位文本，不解析 XML。 */
    private String contentFor(long localType, String text, MessageType type) {
        return switch (type) {
            case TEXT -> text;
            case IMAGE -> "[图片]";
            case EMOJI -> "[表情]";
            case SYSTEM -> text.isEmpty() ? "[系统消息]" : text;
            default -> "[其他类型 local_type=" + localType + "]";
        };
    }

    private String stripGroupPrefix(String text) {
        if (text == null) return "";
        return GROUP_PREFIX.matcher(text).replaceFirst("");
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new ImportException("sourceHash 计算失败", "MessageMapper.sha256", e);
        }
    }
}
