# JEVE AI 分析流水线（Pipeline）

本目录定义从聊天记录到 AI 输出的完整处理流程。

预期阶段：
1. 聊天记录标准化（说话人锁定、时间戳、证据编号）
2. 关键窗口切片（事件点 + 上下文）
3. 事实提取（主动次数、兑现、时间间隔、明确原话）
4. 行为模式聚合
5. 调用 Router 选择 Skill / Knowledge
6. 拼装 Prompt（Global Rules + Knowledge + 事实 + 当前上下文）
7. 调用 AI Provider
8. Filters 后处理
9. 四层输出（事实 / AI观察 / 可能性 / 未知）

当前为空骨架，下一阶段填充。
