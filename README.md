# RUI

> **Your AI Relationship Assistant**
>
> 一个面向真实聊天场景的 AI 关系助手。  
> 不需要复制聊天记录，不需要手动整理上下文，让 AI 直接从你的微信聊天中获得持续、真实的对话上下文。

---

## ✨ RUI 是什么？

RUI 并不只是一个“AI 帮你生成聊天回复”的工具。

它更希望解决的是一个更大的问题：

> **当 AI 真正了解你正在和谁聊天、你们聊过什么、最近发生了什么，它能不能成为一个真正的关系助手？**

传统的 AI 聊天工具通常需要用户：

```text
复制聊天记录
      ↓
粘贴给 AI
      ↓
告诉 AI 对方是谁
      ↓
解释上下文
      ↓
询问应该怎么回复
```

RUI 希望把这个过程变成：

```text
微信聊天
   ↓
RUI 自动读取
   ↓
实时获得新的消息
   ↓
构建当前聊天上下文
   ↓
选择合适的 Skill
   ↓
AI 理解当前场景
   ↓
给出结果
```

**用户不应该成为 AI 的“数据搬运工”。**

---

# 🚀 核心亮点

## 1. 直接读取微信本地聊天数据

这是 RUI 当前最重要的能力之一。

RUI 可以直接读取 Windows 微信本地数据，而不是要求用户：

- 导出聊天记录
- 复制聊天内容
- 截图
- 手动上传数据库
- 手动填写数据库路径
- 手动填写微信号

正常情况下，用户只需要：

> **微信已经安装并登录 → RUI 自动检测 → 选择账号 → 选择联系人**

即可读取对应的聊天记录。

当前 RUI 已经完成真实环境验证：

```text
Windows
    ↓
微信本地数据
    ↓
WeChat DB
    ↓
RUI Database Message Source
    ↓
chat_message
    ↓
AI Context
```

这意味着 RUI 不再依赖“用户主动把聊天记录交给 AI”。

---

# ⚡ 2. 微信消息实时进入 RUI

不仅可以读取历史聊天记录，RUI 还已经实现了 **微信本地数据库实时监听**。

当对方发送新的微信消息时：

```text
对方发送消息
      ↓
微信本地数据库产生新记录
      ↓
RUI 实时监听
      ↓
识别新消息
      ↓
写入 RUI
      ↓
聊天记录 / AI 上下文更新
```

实际运行中，新消息可以在大约 **1 秒级**进入 RUI。

因此 RUI 不再是：

> “打开软件 → 手动同步一次 → AI 分析一次”

而逐渐变成：

> **RUI 一直知道当前聊天发生了什么。**

这也是 RUI 与普通 AI 聊天工具之间非常重要的区别。

---

# 🧠 3. Skill：不是一个 AI，而是一套 AI 行为

RUI 的另一个核心设计是 **Skill Runtime**。

我们没有把所有 AI 行为写死在代码里。

RUI 将：

```text
Function
+
Skill
+
Context
+
Knowledge
+
AI Model
```

组合成一次 AI 执行。

其中：

### Function

决定：

> **这一次 AI 要做什么？**

例如：

- 快速回复
- 详细分析
- 深度观察

### Skill

决定：

> **AI 应该以什么角色、方式和思维方式完成这个任务？**

因此，同样一段聊天记录，不同 Skill 可以得到完全不同的结果。

例如：

### 普通聊天 Skill

> 她可能是在关心你最近的状态，可以自然地回复一下最近在忙什么。

### 恋爱分析 Skill

> 这句话表面是在询问你的近况，但结合近期互动频率来看，也可能包含主动寻求互动的意味。

### 狗头军师 Skill

> 别急着开始写工作总结，人家是在给你递话题。顺着她聊就行。

**数据相同，AI 行为不同。**

这就是 RUI 的 Skill 模型。

---

# 🧩 4. Skill 不等于固定 Prompt

RUI 并不希望把 Skill 做成：

> “换一个 Prompt = 换一个模式”

而是正在建立自己的 **Skill Runtime**。

标准 Skill 可以包含：

```text
skill/
├── SKILL.md
├── scripts/
├── references/
└── assets/
```

RUI Runtime 负责：

```text
Skill Discovery
      ↓
Skill Registry
      ↓
Skill Compatibility
      ↓
Skill Activation
      ↓
Context Adapter
      ↓
Prompt Assembly
      ↓
AI Execution
      ↓
Output Runtime
```

这样，未来一个 Skill 不一定只能服务于 RUI。

RUI 希望能够逐渐兼容更广泛的 Agent Skill 生态。

---

# 💬 5. Quick Reply：从“生成一句话”变成“理解当前聊天”

RUI 当前的快速回复并不是简单的：

```text
聊天记录 → LLM → 一句话
```

而是：

```text
当前联系人
      +
最近聊天上下文
      +
当前消息
      +
Skill
      +
Knowledge
      ↓
AI
      ↓
当前最有价值的回复建议
```

用户可以根据自己的需求选择不同 Skill。

AI 不只是负责“说什么”。

而是由 Skill 决定：

- 怎么理解这句话
- 应该关注什么
- 应该采取什么沟通策略
- 应该用什么表达方式
- 哪些信息值得提醒用户

最终：

> **用户决定是否发送。**

RUI 不替用户擅自发送消息。

---

# 🔍 6. 从 Quick Reply 走向 Deep Observation

这是 RUI 下一阶段非常重要的发展方向。

现在的 Quick Reply 解决的是：

> **“这句话我应该怎么回复？”**

而未来的 Deep Observation 希望解决：

> **“我们的关系到底发生了什么？”**

例如长期观察：

```text
聊天频率
    ↓
主动程度
    ↓
互动模式
    ↓
情绪变化
    ↓
行为变化
    ↓
关系变化
```

最终 AI 不只是回答：

> “这句话怎么回？”

而是能够逐渐回答：

> “最近你们的关系发生了什么变化？”

---

## 🔭 Deep Observation 的长期方向

未来 RUI 希望能够建立：

### 对话层

分析单次对话。

↓

### 行为层

观察双方长期行为。

↓

### 情绪层

观察情绪变化和互动状态。

↓

### 关系层

判断关系是否正在：

```text
升温
稳定
疏远
冲突
恢复
恶化
```

↓

### 长期观察

最终形成：

> **Relationship Timeline**

让 AI 不只是看到一句话，而是看到一段关系的变化轨迹。

---

# 🗂️ 7. 多数据源架构

RUI 并不希望把系统绑定在某一种数据获取方式上。

当前已经探索并实现：

```text
MessageSource
      ↓
Observed Message
      ↓
Normalizer
      ↓
Unified Message
      ↓
Identity / Confidence
      ↓
Chat Message
      ↓
AI Context
```

未来可以接入：

```text
                 ┌─ WeChat Database
                 │
MessageSource ───┼─ RapidOCR
                 │
                 ├─ PaddleOCR
                 │
                 ├─ Vision Model
                 │
                 └─ UIAutomation
```

这意味着：

> **数据来源可以变化，但上层 AI 不需要跟着变化。**

例如：

- 数据库读取负责高可信消息
- OCR 负责视觉观察
- Vision Model 可以处理复杂视觉场景
- UIAutomation 可以作为另一种采集方式

这些最终都进入统一消息层。

---

# 🛡️ 8. 消息身份与数据一致性

真实聊天系统最难的问题之一，并不是“把文字 OCR 出来”。

而是：

> **AI 怎么知道这到底是不是同一条消息？**

例如 OCR 连续看到：

```text
你好
你好
你好
```

它们可能是：

- 同一条消息被连续识别
- 三条真正不同的消息
- OCR 内容发生变化
- 滚动过程中重复出现

因此 RUI 正在建立：

```text
Canonical Message
        +
Message Observation
        +
External Identity
        +
Identity Resolver
        +
Confidence
```

将：

> **“看到了一段文字”**

与：

> **“确认这是现实世界中的哪一条消息”**

分开。

这也是 RUI 从一个简单 Demo 向真实聊天系统演进的重要一步。

---

# 🏗️ 技术架构

当前整体架构可以概括为：

```text
                 ┌────────────────────┐
                 │      WeChat        │
                 └─────────┬──────────┘
                           │
                  Message Sources
                           │
             ┌─────────────┴─────────────┐
             │                           │
      WeChat Database                 OCR
             │                           │
             └─────────────┬─────────────┘
                           ↓
                  Message Observation
                           ↓
                  Identity / Normalize
                           ↓
                    Chat Message
                           ↓
                 Relationship Context
                           ↓
                    Context Builder
                           ↓
                       Function
                           +
                        Skill
                           +
                      Knowledge
                           ↓
                     LLM Provider
                           ↓
                    Output Runtime
                           ↓
                      RUI Interface
```

---

# 🛠️ 技术栈

目前项目主要使用：

| 技术 | 用途 |
|---|---|
| Java 17 | 核心应用 |
| JavaFX | Windows 桌面应用 |
| WebView | HTML/CSS/JS UI |
| Maven | 项目构建 |
| SQLite | 本地业务数据 |
| Python | 微信数据读取 / 数据桥接 |
| WeChat DB | 微信本地聊天数据 |
| RapidOCR | OCR 数据源 |
| DeepSeek | AI Provider |
| Skill Runtime | AI 行为运行时 |

---

# 🎨 RUI 的设计方向

RUI 的 UI 并不希望做成传统的“程序员工具”。

当前界面采用：

> **Warm White + Purple + Liquid Glass**

整体设计方向参考现代桌面应用的轻量化、低干扰视觉语言。

核心原则：

- 简洁
- 低干扰
- 信息密度适中
- AI 是核心，而不是复杂设置
- 重要操作保持简单
- 尽量让用户感觉不到后台的数据处理过程

最终希望做到：

> **复杂的东西在后台，用户看到的是简单的结果。**

---

# 🗺️ Roadmap

RUI 目前仍处于快速发展阶段。

### ✅ 已完成

- [x] Windows 桌面应用
- [x] 微信本地聊天数据读取
- [x] 微信环境自动检测
- [x] 多账号检测基础能力
- [x] 联系人选择
- [x] 微信数据库历史消息读取
- [x] 微信数据库实时监听
- [x] 实时消息进入 RUI
- [x] AI 上下文自动更新
- [x] Quick Reply
- [x] Skill Runtime
- [x] Skill 驱动的 AI 行为
- [x] 多数据源抽象
- [x] OCR 数据源
- [x] Message Identity / Observation 数据模型
- [x] 跨数据源消息一致性处理
- [x] 基础 UI / Liquid Glass 视觉体系

### 🚧 当前发展

- [ ] Skill 生态进一步完善
- [ ] Knowledge Runtime
- [ ] 更完善的关系上下文
- [ ] 更丰富的分析 Skill
- [ ] 数据源可靠性进一步提升

### 🔭 Future

- [ ] Deep Observation
- [ ] Relationship Timeline
- [ ] 长期关系趋势分析
- [ ] 情绪变化观察
- [ ] 行为模式观察
- [ ] 关系阶段识别
- [ ] 更多 Message Source
- [ ] 更开放的 Agent Skill 生态

---

# 🧭 RUI 最终想做什么？

RUI 的最终目标并不是：

> **“帮你写一句更好的微信回复。”**

而是：

> **让 AI 真正进入你的真实社交上下文。**

从：

```text
帮我回复一句话
```

逐渐发展到：

```text
帮我理解这次对话
```

再到：

```text
帮我理解最近发生了什么
```

最终：

```text
帮我看见这段关系正在发生什么变化
```

---

# ⚠️ 当前状态

RUI 目前仍处于开发阶段。

部分功能仍在持续迭代，尤其是：

- Skill Runtime
- 数据源
- 消息身份识别
- Relationship Context
- Deep Observation

项目目前主要针对 **Windows + 微信桌面端** 环境进行开发和验证。

---

# 🔐 Privacy

RUI 的一个重要方向是：

> **尽可能让聊天数据留在用户自己的设备上。**

微信聊天数据读取、消息存储和上下文构建均以本地运行作为重要设计方向。

不过由于 RUI 仍处于开发阶段，实际使用前请务必注意：

- API Key 不要提交到 Git
- 不要将真实微信数据库提交到公开仓库
- 不要将真实聊天记录、媒体文件提交到 GitHub
- 不要公开 `application-local.properties`
- 使用自己的 API Provider 时请自行确认数据隐私策略

---

# 📌 Project Status

**RUI is an experimental AI relationship assistant under active development.**

它现在还不是一个完成品。

但它正在尝试解决一个比“AI 回复生成”更有意思的问题：

> **如果 AI 能够持续看到真实的聊天、理解上下文、调用不同 Skill，并长期观察人与人之间的互动，它最终能不能真正成为一个关系助手？**

RUI 正在探索这个问题。

---

## ⭐ 如果你对这个项目感兴趣

RUI 后续会逐步开放：

- Skill
- Knowledge
- Message Source
- Relationship Analysis
- Deep Observation

等能力。

欢迎关注项目的发展。
