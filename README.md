# RUI

> **Your AI Relationship Assistant**
>
> 一个将众多 Skill 融合的恋爱关系回复助手。
>
> RUI 可以读取微信本地聊天记录，让 AI 从真实对话上下文中生成回复、分析关系。

## 核心功能

- **微信消息上下文**：读取本地聊天记录，AI 基于真实对话生成建议。
- **多 Skill 回复风格**：内置狗头军师、童锦程视角等 Skill，支持自定义 Skill。
- **前任蒸馏**：基于聊天记录生成人物画像 Skill。
- **详细分析**：情绪、事实、模式多维度分析。

## 快速开始

1. **环境要求**：Java 17+（运行时）、Maven（构建）。
2. **构建**：`mvn clean package`
3. **首次启动**：应用会引导你配置 AI API Key（base URL、model、api key），加密存储在本地 SQLite。
4. **数据库**：默认使用 SQLite（`data/jeve.db`），首次启动自动建表。

## 可选组件

- **实时微信桥接**：需要额外的 `wechatapi-main`（Python）环境，实现新消息秒级进入。该组件**不包含在本仓库**，未安装时不影响数据库蒸馏和基本回复功能。
- **微信导出文件解析**：需要 Python 3.9+，用于解析导出的聊天记录文件。

## 目录结构

- `src/main/java/` — Java 源码
- `src/main/resources/web/` — WebView 前端
- `skills/` — 用户 Skill 目录
- `knowledge/` — 内置恋爱知识体系

## 许可

本项目仅供学习研究使用。
