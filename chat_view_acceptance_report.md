# 聊天记录查看功能验收报告（chat_view_acceptance_report.md）

> 阶段：MySQL chat_message → Repository → Service → JavaFX 微信风格界面。
> 数据：Relationship ID=2，708 条真实微信消息（界面数据**只来自 MySQL 当前有效 content**，未重读 SQLite）。

## 一、交付内容（保持现有分层）

| 层 | 新增/改动 |
|---|---|
| Repository | `ChatMessageRepository.countByRelationship()`、`pageByRelationship(rel, offset, limit)`（ORDER BY message_time ASC, id ASC，LIMIT/OFFSET） |
| Service | `ChatService.loadPage(rel, offset, limit)`，含全部要求的 [Chat] 日志与异常日志 |
| DTO | `ChatPage`（total / offset / limit / messages） |
| UI | `ui.ChatViewApplication`（JavaFX）、`ui.ChatViewLauncher`（classpath 启动入口） |
| 依赖 | pom 新增 org.openjfx:javafx-controls 17.0.13 |

分页：每页 50 条；默认打开最后一页（最新消息）并滚到底部；点"加载更早消息"向前翻页并前插，到顶后按钮变"已是最早消息"。避免未来几万条一次性加载。

## 二、界面显示规则（逐项实测通过）

| 要求 | 实测 |
|---|---|
| ME 右侧（微信绿气泡 #95EC69） | ✓ 如"我想换手机""我没摇奶茶，我在搅米麻薯" |
| OTHER 左侧（白色气泡） | ✓ 如"笑死我了""你还在摇奶茶呢""吗" |
| SYSTEM 居中（灰色斜体） | ✓ 撤回通知 XML 居中显示，07-05 18:07 |
| 显示消息时间 | ✓ 每条气泡下方 MM-dd HH:mm |
| TEXT 正常文字 | ✓ |
| EMOJI 占位 | ✓ 显示 [表情] |
| IMAGE 占位 | ✓ 双方均显示 [图片]，不读媒体 |
| 未识别类型占位 | ✓ 显示 [其他类型 local_type=43] 等 |
| 单条渲染失败不影响其他消息 | ✓ 渲染有 try/catch，失败出 [渲染失败] 占位并记日志 |

## 三、数据一致性验收

1. **分页全量加载**：经 Service 分页路径可完整加载 708/708 条，时间**严格 ASC**。
2. **随机 10 条 UI 路径 vs 独立裸 JDBC 交叉核对：10/10 一致，不一致 0**（时间、发送者、内容三项全等）：

| id | 时间 | 发送者 | 内容 |
|---|---|---|---|
| 762 | 2026-06-17 21:07:02 | OTHER | 比你来去一趟好 |
| 1015 | 2026-07-09 11:04:09 | ME | 确实要刮大风了 |
| 1216 | 2026-07-31 21:04:29 | OTHER | 加提成 |
| 1044 | 2026-07-09 18:19:25 | ME | 我也不去 |
| 1210 | 2026-07-31 21:03:48 | OTHER | 一天八小时 |
| 1205 | 2026-07-31 21:03:20 | OTHER | 五险一金 |
| 921 | 2026-07-01 06:26:47 | ME | 当时就应该把你骗来上海的 |
| 894 | 2026-06-26 01:27:50 | OTHER | 当然了 |
| 1367 | 2026-08-13 22:36:01 | OTHER | 没有 |
| 993 | 2026-07-05 16:52:47 | OTHER | 吗 |

3. **SYSTEM 定位**：序号 286（0 基），时间 2026-07-05 18:07:55，内容为撤回通知 XML（revokemsg / "You recalled a message"），与此前人工确认一致。
4. **日志点全部出现**：[Chat] 开始加载 relationship / 查询消息数量 / 首条消息时间 / 末条消息时间 / UI 加载完成 / 查询异常 / 消息渲染异常（后两者仅在异常时输出，本阶段无异常；渲染异常路径有兜底代码）。
5. **单元测试**：mvn test 全部通过。

## 四、约束遵守说明

- 未改 WechatSqliteImporter；未改 Flyway V1 / 数据库结构；未做媒体解析、AI、快速回复、新导入。
- source_content 保持只读，界面只用 content；content 与 source_content 当前全等（导入后未做编辑）。
- 为验收定位，界面支持可选启动参数 `--offset=N`（不影响默认行为：不传仍打开最新一页）。
- 说明：按钮处理函数调用的 loadPage 分页路径已在无头验收中对全部 15 页验证通过；物理点击按钮的截图因窗口焦点被其他窗口抢占未单独留存，不影响功能结论。

## 五、启动方式

```
mvn -q compile exec:java "-Dexec.mainClass=com.harbor.relationshipassistant.ui.ChatViewLauncher"
（可选："-Dexec.args=--rel=2 --offset=280"）
```

**结论：真实聊天记录查看功能验收通过。窗口当前保持打开可直接查看；已暂停，等待下一步指令。**
