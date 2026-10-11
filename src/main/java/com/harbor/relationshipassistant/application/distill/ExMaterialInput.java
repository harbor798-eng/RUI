package com.harbor.relationshipassistant.application.distill;

/**
 * 单一材料输入项。
 * type 取值：
 *   DB          —— 从 RUI 数据库读取（path 忽略，使用 relationshipId）
 *   WECHAT_FILE —— 微信导出文件（.txt/.html/.csv），走原版 wechat_parser.py
 *   SMS_FILE    —— 短信导出（.xml/.csv/.txt），走原版 sms_parser.py
 *   SOCIAL_FILE —— 社交媒体导出（.json/.txt），走原版 social_media_parser.py
 *   PHOTO_DIR   —— 照片目录，走原版 photo_analyzer.py（EXIF 时间线）
 *   QQ_FILE     —— QQ 聊天记录导出（.txt/.html/.csv），走 Java 内置 QQ 解析器
 *
 * iMessage 在 Windows 不可用（原版依赖 macOS chat.db），不列入。
 */
public record ExMaterialInput(String type, String path) {
    public static ExMaterialInput db() { return new ExMaterialInput("DB", null); }

    public boolean isPythonBased() {
        return switch (type) {
            case "WECHAT_FILE", "SMS_FILE", "SOCIAL_FILE", "PHOTO_DIR" -> true;
            default -> false;
        };
    }
}
