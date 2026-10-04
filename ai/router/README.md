# JEVE Skill / Knowledge 路由器（Router）

本目录负责决定：
- 当前 AI 场景应该启用哪个 Skill
- 该 Skill 下应该加载哪些 Knowledge 片段
- 是否叠加 JEVE Global Rules

预期输入：当前 AI 任务类型（快速回复 / 详细分析 / AI观察 / 深度观察）、当前关系阶段、用户问题关键词。
预期输出：需要拼接到 Prompt 中的 Knowledge 片段列表。

当前为空骨架。不要硬编码 `if ("goutoujunshi".equals(...))`。
