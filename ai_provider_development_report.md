# AI Provider 基础设施开发报告

日期：2026-09-26
阶段：V1 AI 基础设施（仅 DeepSeek，架构预留多厂商）

## 1. 修改 / 新增文件

| 文件 | 动作 | 说明 |
|---|---|---|
| `infrastructure/ai/DeepSeekProvider.java` | 新增 | DeepSeek 命名厂商实现（OpenAI 兼容），默认 base/model |
| `infrastructure/ai/OpenAICompatibleProvider.java` | 修改 | 错误细分：401/403/404/429/5xx/超时/网络异常 → 友好中文提示；日志改 `[DEEPSEEK_*]` |
| `application/ai/AiConfigService.java` | 新增 | 配置加密存取、Provider 工厂、测试连接 |
| `ui/AiSettingsDialog.java` | 新增 | AI 设置对话框（Provider/Key 掩码/Model/Base URL/保存/测试连接） |
| `ui/ChatViewApplication.java` | 修改 | 顶栏加「AI 设置」按钮 |
| `app/AiConfigVerifyMain.java` | 新增 | 配置层无头验收（9 项） |

未改动：SQLite/HTML Import、ChatService/ChatView 渲染、Profile、V1/V2 Flyway、rel=2 的 708 条消息。

## 2. Provider 抽象设计

- 接口 `AIProvider`（已有）：`generate(AIRequest)` / `getProviderName()` / `getSupportedModels()`。
- 线协议实现 `OpenAICompatibleProvider`（已有，/chat/completions + Bearer）。
- 厂商实现 `DeepSeekProvider extends OpenAICompatibleProvider`，`getProviderName()=DEEPSEEK`。
- 工厂 `AiConfigService.buildProvider()` 按数据库 active 行的 `provider_name` 路由；未知厂商抛明确异常。未来新增厂商 = 新增一个 Provider 子类 + 工厂分支，业务层（QuickReplyService 等）只依赖 `AIProvider`。
- **当前仅实现 DeepSeek Provider**；抽象已支持未来扩展，其他厂商未实现。

## 3. DeepSeekProvider 实现

- base 默认 `https://api.deepseek.com`，model 默认 `deepseek-chat`，均可在 UI 修改。
- 测试连接：system="You are a helpful assistant." + user="请回复：AI连接测试成功"，temperature=0、max_tokens=50。
- HTTP 复用 JDK `java.net.http.HttpClient`（连接超时 15s，请求超时 60s），未引入新 HTTP 框架；Jackson 构建/解析 JSON。

## 4. API Key 存储方式

- 复用 `ai_provider_config.encrypted_api_key`（V1 已有表，无需新 migration）。
- 落库前 `AesCryptoService.encrypt`（AES-GCM，复用 Profile 同一把本地主密钥）；读取后内存解密用于 Authorization 头。
- UI 只显示掩码（如 `sk-****7890`）；保存时留空=保留旧 Key。
- Key 不写日志、不写 Git、不进异常消息、不进 AI Context。

## 5. 数据库变化

**无新增 migration。** 复用 V1 `ai_provider_config(provider_name, base_url, model, encrypted_api_key, is_active, created_at, updated_at)`。

## 6. UI 变化

聊天窗口顶栏新增「AI 设置」按钮 → 设置对话框：
- Provider 下拉（当前仅 DeepSeek，禁用态，为未来厂商预留）
- API Key 密码框 + 已配置掩码状态
- Model / Base URL 可编辑
- 「保存」+「测试连接」（测试时按钮禁用防重复；成功/失败弹窗明确提示，失败原因友好）

## 7. HTTP 请求方式

JDK HttpClient → POST `{baseUrl}/chat/completions`，Header `Authorization: Bearer <key>`，JSON body 由 Provider 内部构建；业务层不碰 JSON。

## 8. 错误处理

- 401/403 → "AI API Key 无效或未授权"
- 404 → "接口地址或模型不存在"
- 429 → "请求频率受限"
- 500/502/503 → "服务暂时不可用"
- HttpTimeoutException → "连接超时"
- IOException → "无法连接，请检查网络"
- JSON 解析异常 → "响应格式异常"
- 无配置 → "请先配置 DeepSeek API Key"
- 全部以 `AIException` 抛出，UI try/catch 弹窗，不崩 JavaFX 主线程。

## 9. 日志设计

新增：`[AI_CONFIG_LOAD]` / `[AI_CONFIG_SAVE]` / `[AI_PROVIDER_INIT]` / `[DEEPSEEK_REQUEST]` / `[DEEPSEEK_SUCCESS]` / `[DEEPSEEK_ERROR]` / `[DEEPSEEK_TIMEOUT]`。记录 provider/model/status/耗时，**不记录 Key、Authorization、请求体敏感内容**。

## 10. 测试结果

- `mvn compile` 通过；`mvn test` 全部通过（既有 HTML/聊天测试无回归）。
- `AiConfigVerifyMain` 9 项全 PASS：缺配置报错、加密落库无明文、active 标记、掩码正确、model 读取、buildProvider、空 Key 保存保留旧 Key、rel=2 仍 708 条、测试数据已清理。
- `mvn javafx:run` 实机启动正常，加载 708 条消息不崩。

## 11. 真实 API E2E 是否执行

**未执行。** 当前环境没有有效 DeepSeek API Key（配置表为空）。已完成全部编译期/配置层测试；请在「AI 设置」中填入真实 Key 后点「测试连接」进行真实联调。本报告未包含任何 Key。

## 12. 已知问题

- Provider 下拉只有 DeepSeek 且禁用（按本阶段要求）。
- 未实现多厂商、3×3 快速回复、AI Context（private profile 默认不进 AI，后续 ContextBuilder 再约束）。
- 实机对话框内点选「测试连接」未在无头环境触发，按钮与异常路径已在代码层覆盖。

## 13. 下一阶段建议

1. 用户填入真实 DeepSeek Key，实测「测试连接」返回。
2. 下一阶段单独设计 **AI Context（ContextBuilder：最近 N 条消息 / Profile 可见项 / 关系阶段 / private 禁入）+ 3×3 快速回复**。
