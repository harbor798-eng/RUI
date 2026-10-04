package com.harbor.relationshipassistant.infrastructure.importer.wechat;

/**
 * 媒体扩展点（本阶段不实现）。
 *
 * <p>后续图片/视频/语音/文件导入时，按 message_local_id + tableHash 关联
 * message_resource.db / hardlink.db / media_0.db 与磁盘 media/ 目录。
 * 正式媒体导入不依赖 _media_keys.json。</p>
 */
public interface WechatMediaHandler {

    /** 是否支持该 local_type。 */
    boolean supports(long localType);

    /** 把原始行解析成媒体占位信息（第二阶段实现）。 */
    String resolve(WechatRawMessage raw);
}
