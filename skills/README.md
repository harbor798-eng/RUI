# JEVE Skills 目录

本目录存放 JEVE 可加载的 Skill。每个 Skill 是一个独立文件夹。

## Skill 最小规范

```
skills/<skill-id>/
├── manifest.json      # 必填，见下
├── README.md          # 可选但推荐
├── knowledge/         # 可选，该 Skill 使用的知识文件
│   ├── xxx.md
│   └── ...
└── source/            # 可选，原始来源存档（LICENSE、原始 SKILL.md 等）
```

## manifest.json 最小字段

```json
{
  "id": "skill-id",
  "name": "人类可读名称",
  "version": "1.0.0",
  "description": "一句话说明",
  "type": "knowledge-skill",
  "enabled": true,
  "knowledgePath": "knowledge"
}
```

## 设计原则

- Skill 与 JEVE Global Rules 解耦：Skill 不修改 JEVE 全局行为。
- Skill 与 Knowledge 解耦：未来可让多个 Skill 共享同一份 Knowledge，或让 Skill 不带 Knowledge。
- 不硬编码 `if ("goutoujunshi".equals(...))`。
- 用户未来可把自己的 Skill 文件夹放入本目录，由 SkillLoader 扫描识别（加载逻辑下一阶段实现）。

## 当前已安装

- [goutoujunshi](./goutoujunshi/) — 恋爱关系分析知识库（外部参考 Skill）
