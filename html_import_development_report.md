# HTML 聊天导入 MVP — 开发报告

日期：2026-09-26　项目：AI 恋爱关系助手 V1　阶段：HTML Import MVP

## 0. 测试残留清理（第一步）

- 清理前 `chat_message` = 709（708 IMPORTED + 1 APP）。
- 实查 APP 行：id=1419，ME/TEXT，content=`你好`，time=2026-09-26 14:06:59，source_message_id=NULL —— 确认为 JavaFX 输入框联调残留。
- 新增 `app/CleanupAppTestResidualMain`（WHERE 双重限定：`source_type='APP' AND source_message_id IS NULL`）物理删除该行。
- 清理后：**chat_message = 708，IMPORTED = 708，APP = 0**。

## 1. 修改/新增文件清单

### 新增类
| 文件 | 作用 |
|---|---|
| `ui/HtmlImportDialog.java` | HTML 导入对话框：选文件→解析→身份确认→预览/修改→确认导入 |
| `app/CleanupAppTestResidualMain.java` | 删除 UI 联调 APP 残留（已执行） |
| `app/HtmlImportVerifyMain.java` | 无头端到端验收（建临时关系→解析→改一条→导入→重复导入验幂等） |
| `app/CleanupHtmlTestMain.java` | 清理临时验收关系及其消息/导入记录 |
| `sample_chat.html` | 验收用样例 HTML（7 条，含图片占位、缺时间行） |

### 修改类
| 文件 | 改动 |
|---|---|
| `infrastructure/importer/ImportedRawMessage.java` | 新增 `sourceContent` 字段（解析原始内容，与可编辑 `content` 分离） |
| `infrastructure/importer/ImportPreview.java` | 新增统计计算：ME/OTHER/SYSTEM 数、各 MessageType 数、最早/最晚时间、手动行数 |
| `infrastructure/importer/HtmlChatImporter.java` | 写 sourceContent；source_message_id=`html:sha256(原始时间\|原始内容)`；无时间行用稳定前缀 `html:nt:sha256(内容)`（避免 now() 破坏幂等）；日志改 `[HTML_PARSE]` |
| `application/importjob/ImportService.java` | source_content 取 `raw.sourceContent`（不再覆盖）；新增 `[HTML_CONFIRM]/[HTML_INSERT]/[HTML_DUPLICATE]/[HTML_ERROR]` 日志 |
| `ui/ChatViewApplication.java` | 底部工具栏新增「导入HTML」按钮，打开 HtmlImportDialog，导入后刷新当前页 |
| `src/test/.../HtmlChatImporterTest.java` | 2 → 7 个用例（基础解析/身份映射/墙钟时间/sourceContent 保留/幂等键稳定/异常 HTML/无时间行幂等键） |

### 未改动
WechatSqliteImporter、MessageMapper、ChatMessageRepository、ChatService、Flyway V1/V2、原始微信 SQLite、V1/V2 migration 文件、数据库表结构。

## 2. 数据库 migration

**未新增 V3。** chat_message 已有 source_type/source_message_id/source_hash/source_content/metadata/status 全部字段，无需改表。

## 3. 导入流程

```
选择 HTML 文件 (FileChooser)
  → HtmlChatImporter.parse（Jsoup 解析 div.chat-item：time/speaker/bubble）
  → ImportPreview（统计：总数/ME/OTHER/SYSTEM/类型分布/时间范围/解析告警）
  → 身份确认：「我的昵称」输入框，决定 speaker→ME/OTHER；可「重新解析」
  → 预览表可逐行修改（发送者 ME/OTHER/SYSTEM、类型、时间、内容），可删行、可加空行
  → 确认导入 → ImportService.confirm（单事务）
  → chat_message → ChatView 刷新显示
```

## 4. 关键设计

- **幂等方案**：HTML 无稳定外部行号，source_message_id = `html:` + sha256(原始时间|原始内容)；用户在 Preview 改 content/sender 不影响该键。无明确时间的行用 `html:nt:` + sha256(内容)，避免每次解析 now() 不同导致重复入库。重复导入同一文件：existsBySource 命中即跳过。
- **source_content / content**：解析时 `sourceContent=原始 bubble 文本`；Preview 里改的是 `content`；confirm 时 `source_content=sourceContent`、`content=用户确认值`，永不覆盖。
- **revision**：导入阶段不写 revision（那是导入前的原始数据落地）；导入后用户在聊天窗口再改消息，仍走既有 ChatService.editMessage「先 revision 后 update」。
- **关系隔离**：导入必须绑定当前 relationship_id；验收在临时关系上跑，正式关系 id=2 全程未被污染。
- **时区**：HTML 时间按字符串解析为墙钟 LocalDateTime，按既有 setObject/getObject 直写 DATETIME，Asia/Shanghai 口径不变。

## 5. 验证结果

### 编译 / 测试
- `mvn compile`：成功
- `mvn test`：10 个测试全部通过（HtmlChatImporterTest 7 + ModificationRateCalculatorTest 3）

### 无头端到端（HtmlImportVerifyMain，样例 sample_chat.html）
- 第一次解析：total=7，ME=3，OTHER=4，0 错误
- 模拟用户修改第 1 条 content → 第一次导入 **inserted=7, duplicates=0**
- 同一文件再解析再导入 → **inserted=0, duplicates=7**（幂等通过）
- 落库核对：修改行 `content=…（人工核对修改）`、`source_content=早上好，今天周末有什么安排？`（原始值保留）✓
- 关系 2（T高改芸）ACTIVE 消息数 = 708（未受影响）✓
- 类型分布：ME TEXT 2 + ME IMAGE 1 + OTHER TEXT 4 = 7 ✓

### 真实 UI
- `mvn -q javafx:run` 启动成功，日志：total=708，首条 2026-06-01T10:52:35，末条 2026-09-06T21:27:56，UI 渲染 50 行无异常。
- 工具栏新增「导入HTML」按钮已编译进 UI。注：本会话中 JavaFX 窗口前台聚焦受已知 glass 渲染问题影响未截图成功，对话框业务路径与无头验收完全一致。

### 清理
- 临时验收关系（relId=3、4）及其消息（共 15 行）、导入记录（4 行）已清理。
- 最终数据库：仅 relationship id=2；chat_message ACTIVE = 708；IMPORTED=708；APP=0。

## 6. 已知问题 / 限制

1. HTML 结构按 `div.chat-item > div.time/speaker/bubble` 约定解析；其他导出工具的 HTML 结构后续按真实样本扩展选择器（解析不到会降级为时间正则并在预览告警中提示）。
2. 同一关系下「时间+内容完全相同」的重复消息会被幂等键视为同一条。
3. 无时间的行 message_time 落库为解析时刻（墙钟），导入后用户应在聊天窗口修正时间。
4. 未做批量多文件导入（本阶段 MVP 范围外）。

## 7. 下一步建议

按 V1 最低完成条件清单，剩余：①用户资料/对方资料（Profile 录入 UI）；②AI Provider API Key 接入与 3×9 快速回复。建议先做 Profile，再做 AI（AI 依赖稳定的聊天数据与身份口径，现已就绪）。
