# goutoujunshi（狗头军师）Skill

> 本目录是 JEVE 中安装的第一个外部知识库 Skill。
> 它**不是** JEVE 的核心业务代码，也不会自动变成 JEVE 的系统提示词。

## 来源

- 原项目：[shengjidaguai-china/goutoujunshi](https://github.com/shengjidaguai-china)
- 本地原始仓库：`goutoujunshi-main/goutoujunshi-main/`（保留未修改）
- 许可证：见 `source/LICENSE`
- 原始 SKILL.md 存档：`source/ORIGINAL_SKILL.md`

## 目录结构

```
goutoujunshi/
├── manifest.json          # Skill 元数据（最小规范）
├── README.md              # 本文件
├── knowledge/             # 20 个原始 Markdown 知识文件（未改写、未蒸馏）
│   ├── 01-证据分级与内容边界.md
│   ├── 02-亲密关系心理学总论.md
│   ├── ...
│   └── 20-经典社交体系的机制、证据与风险边界.md
└── source/
    ├── LICENSE            # 原项目许可证
    └── ORIGINAL_SKILL.md  # 原项目 SKILL.md 存档（不作为 JEVE 系统提示词加载）
```

## 当前状态

- `knowledge/`：已迁移 20 个文件，**逐字保留原文**，未做总结/改写/合并。
- `practical/`：原项目 23 个 practical 文件**未迁移**，留待后续按 JEVE 需要逐个引入。
- `source/ORIGINAL_SKILL.md`：仅作研究追溯，**不自动加载为 JEVE 全局 Prompt**。

## JEVE 如何使用本 Skill（架构预留）

未来 JEVE 的 SkillLoader 会扫描 `skills/*/manifest.json`，根据 `id`、`type`、`enabled`、`knowledgePath` 注册 Skill。Router 根据当前 AI 场景决定是否加载本 Skill 下的 knowledge 片段。

当前阶段只搭骨架，不实现加载逻辑。

## 为什么不直接把 goutoujunshi 当核心

JEVE 未来会建立自己的知识体系（基于原始书籍、论文、临床资料）。届时可以直接替换 `skills/goutoujunshi/`，不需要修改 JEVE 的 AI 架构。
