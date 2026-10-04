# AI 恋爱关系助手 V1 - 后端

按《AI 恋爱关系助手 V1 需求文档》《V1 后端技术设计文档》实现。
技术栈：Java 17 + Maven + MySQL 8.x + Flyway + JDBC（HikariCP）+ JDK HttpClient（AI）+ jsoup（HTML 导入）。
V1 不引入 Spring Boot，业务层可独立作为 JavaFX 桌面应用内部模块使用。

## 目录结构（对应技术设计 §4）

```
src/main/java/com/harbor/relationshipassistant/
├── common/           # config(配置) / exception(§44 异常体系)
├── domain/           # relationship / chat / profile / ai / suggestion 等领域模型与枚举
├── application/      # 业务服务
│   ├── relationship/ RelationshipService   # 建关系/阶段变更(同事务写历史)/归档删除
│   ├── chat/         ChatService           # 手动补录/修改(revision)/列表/完整度
│   ├── importjob/   ImportService         # parse→preview→confirm 事务导入+幂等去重
│   ├── profile/     ProfileService         # 档案主记录+条目(AI 确认后才落正式档)
│   └── ai/           QuickReplyService / ContextBuilder / ModificationRateCalculator
├── infrastructure/
│   ├── persistence/  DataSourceFactory(Flyway) + 各 Repository + 审计
│   ├── importer/     ChatImporter 接口 + HtmlChatImporter（可扩展微信SQLite等）
│   ├── ai/           AIProvider 接口 + OpenAICompatibleProvider（兼容 DeepSeek/Doubao/网关）
│   ├── security/     AesCryptoService（API Key/私密字段加密）+ SensitiveDataFilter（脱敏）
│   └── backup/       BackupService（核心表导出 zip）
└── app/ApplicationBootstrap  # 组合根，装配全部 Service
```

数据库 DDL：`src/main/resources/db/migration/V1__init.sql`（技术设计 §41 全部 20 张表 + §42 索引）。

## 核心数据链（V1 验收闭环，§42）

```
QuickReplyService.generateCandidates(relId)
  → 按关系阶段取 3 策略，每策略让 AI 出 3 条 = 9 条候选，落 ai_generated_message
QuickReplyService.selectCandidate(id)          → 状态 SELECTED
QuickReplyService.sendFinal(id, 用户修改后文本) → 单事务内：
     ① ai_generated_message.status=SENT
     ② chat_message 落库，source_ai_message_id 显式指向候选（不靠文本相似度猜来源）
     ③ ai_message_edit_analysis 记录原文/终稿/相似度/修改率/algorithm_version
```

## 运行步骤

1. 启动本地 MySQL 8.x，建库：
   ```sql
   CREATE DATABASE relationship_assistant DEFAULT CHARACTER SET utf8mb4;
   ```
2. 改 `src/main/resources/application.properties`：
   - `db.username` / `db.password`
   - `ai.provider.base-url` / `ai.provider.model` / `ai.provider.api-key`（填 OpenAI 兼容服务）
   - `security.aes-key`（本地 32 字节密钥，勿泄露）
3. 编译测试（不需要 MySQL）：
   ```bash
   mvn clean test
   ```
4. 启动（会自动跑 Flyway 建表）：
   ```bash
   mvn exec:java -Dexec.mainClass=com.harbor.relationshipassistant.app.ApplicationBootstrap
   ```

## 已按文档落实的关键约束

- Raw / Effective 分离：`chat_message.source_content` 保留导入原文，`content` 为当前有效值；用户修改进 `chat_message_revision`。
- AI 判断不直接覆盖用户档案：AI 原始值落 `ai_profile_observation`，确认后才写 `profile_item`。
- API Key 仅用于 Authorization 头，不进日志、不进 Context；手机号/身份证/详细地址由 `SensitiveDataFilter` 在 Context 层拦截。
- 导入/建议确认/AI 发送三处均按 §38 用事务，失败回滚。
- 导入幂等键 `(relationship_id, source_type, source_message_id)`；HTML 无稳定 ID 用内容 SHA-256。

## 与文档"待定项"的对应（§51）

- 修改率算法：当前 = Levenshtein，版本号 `v1-levenshtein` 落库，未来可换算法而不污染历史解释。
- 持久层选裸 JDBC（二选一已取其一）；HTML 解析选择器按主流导出结构实现，真实样本到手后在 `HtmlChatImporter` 扩展即可。
- JavaFX 完整 UI、OCR、微信 SQLite 导入器、Analysis/Timeline/Suggestion 的具体 Service 为下一步 Phase。
