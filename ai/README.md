# JEVE AI 架构（骨架）

本目录是 JEVE 未来 AI 系统的根。当前阶段只搭目录骨架，**不实现任何业务逻辑**。

## 分层

```
ai/
├── rules/       # JEVE 全局 AI 行为规则（与 Skill 无关）
├── filters/     # AI 输出后处理（敏感词、硬约束校验、安全红线）
├── router/      # 决定启用哪个 Skill、加载哪些 Knowledge
├── pipeline/    # 聊天记录 → 事实 → 分析 → 输出 的处理流程
└── prompts/     # 不同场景的 Prompt 模板
```

## 与外部 Skill 的关系

```
JEVE Global Rules (ai/rules)
        ↓
   Router (ai/router)
        ↓
   选择 Skill (skills/*)
        ↓
   加载 Knowledge (skills/<id>/knowledge/)
        ↓
   Pipeline (ai/pipeline) 拼装 Prompt
        ↓
   AI Provider (DeepSeek)
        ↓
   Filters (ai/filters) 后处理
```

## 当前状态

- 本阶段：只建立目录和 README 占位。
- 下一阶段：讨论并填入 JEVE AI Behavior Rules。
- 不要在本目录中硬编码 goutoujunshi。
