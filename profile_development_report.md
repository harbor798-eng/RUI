# Profile 模块开发报告（我的资料 / 对方资料）

日期：2026-09-26
阶段：V1 Profile 模块（用户手动维护）

## 1. 修改 / 新增文件

| 文件 | 动作 | 说明 |
|---|---|---|
| `application/profile/ProfileService.java` | 重写 | 修复原骨架的时区坑；补齐读取/upsert/历史昵称/私密加密读写 |
| `ui/ProfileDialog.java` | 新增 | 我的资料/对方资料 双 Tab 编辑对话框 |
| `ui/ChatViewApplication.java` | 修改 | 顶栏新增「我的资料」按钮 |
| `app/ApplicationBootstrap.java` | 修改 | ProfileService 构造注入 AesCryptoService |
| `app/ProfileVerifyMain.java` | 新增 | 无头 E2E 验收入口 |
| `app/CleanupProfileTestMain.java` | 新增 | 验收临时数据清理入口 |

未改动：WechatSqliteImporter、MessageMapper、ImportService、ChatService、ChatView 消息渲染、HTML Import 全部。

## 2. 数据库变化

**无新增 Flyway migration。** V1 已建齐所需表：

- `profile`：关系下 ME/OTHER 各一条主记录，UNIQUE(relationship_id, owner_type)
- `profile_item`：分类键值条目（category/item_key/item_value/source_type/usage_weight/status）
- `profile_nickname_history`：对方历史昵称（relationship_id + nickname + started_at/ended_at）
- `private_profile_data`：私密字段（relationship_id + owner_type + data_type + encrypted_value + ai_visible）
- `ai_profile_observation`：AI 专用表，本阶段保持空

未修改 V1/V2 已执行 migration；rel=2 聊天消息保持 708 条。

## 3. Profile 数据结构

- 普通可见字段存 `profile_item`，source_type=USER，usage_weight=NORMAL，空值置 ARCHIVED 不物理删。
- 我的资料 19 个字段：基本信息 7、性格 2、兴趣与偏好 4、表达风格 4、个人目标 1、恋爱中的自我 1。
- 对方资料 16 个字段：基本信息 8（含当前昵称/备注）、性格 2、兴趣与偏好 4、敏感话题 1、重要信息 1。
- 私密字段双方各 6 个（手机号/身份证/家庭住址/工作地址/收货地址/私人备注），存 `private_profile_data`，AES-GCM 加密落库，ai_visible=0。
- 对方历史昵称多条，started_at/ended_at 月份粒度，不覆盖当前昵称。

## 4. UI 结构

聊天窗口顶栏「我的资料」按钮 → ProfileDialog：
- Tab「我的资料」：分组表单（基本信息 / 私密信息）+ 保存按钮。
- Tab「对方资料」：基本信息 + 私密信息 + 历史昵称表（昵称/开始/结束月份输入，可添加多条）+ 保存按钮。
- 保存成功/失败均弹窗反馈，不静默失败。

## 5. Relationship 隔离方案

所有查询/写入 SQL 一律带 `relationship_id`（JOIN profile 后过滤），不存在全局 select。E2E 验证：临时关系 relId=5 的 ME 字段对 OTHER 不可见，且 rel=2 的 ME/OTHER 条目为空。

## 6. 保存 / 读取流程

加载：`loadItems(relId, owner)` + `loadPrivate`（解密）+ `loadNicknames` 回填表单。
保存：逐字段 `saveItem`（存在则 UPDATE、不存在则 INSERT、空值归档）；私密字段 `savePrivate`（upsert 加密值，空值删行）；昵称 `addNickname` 插入。
时间口径：全部 `LocalDateTime.now(Asia/Shanghai)` + `setObject` 直写，不用 `Timestamp`/`systemDefault()`。
日志：只打 relationshipId/owner/key/结果，**不打印任何 value**（私密字段不落明文日志）。

## 7. 测试结果

`mvn compile` 通过；`mvn test` 全部通过（HTML Import 等既有测试无回归）。
ProfileVerifyMain E2E 13 项检查全部 PASS：
- ME/OTHER 保存回读一致；互不可见
- 同 key 修改只 UPDATE 不重复 INSERT（仍 1 条）
- 历史昵称 2 条正确
- 私密字段库中密文不含明文、回读解密一致
- rel=2 隔离为空、聊天消息仍 708

## 8. 实际运行验证

`mvn javafx:run` 启动：Hikari 连接成功，加载 rel=2 共 708 条消息，首条 2026-06-01 10:52:35、末条 2026-09-06 21:27:56，UI 渲染 50 条并保持运行，未崩溃。临时数据已由 CleanupProfileTestMain 清理（private=1/nicknames=2/profiles=2），rel=2 复测 708 条。

## 9. 已知问题 / 暂未实现

- 历史昵称无删除按钮（仅添加）。
- usage_weight 字段已建但 UI 未暴露，默认 NORMAL。
- AI 自动生成 Profile、AI observation、3×3 快速回复、API Key 接入均**未做**（按要求本阶段冻结）。
- JavaFX 对话框内实机点选未在无头环境逐一点验，服务层已用无头 E2E 全覆盖。

## 10. 下一阶段建议

1. 用户实机打开「我的资料」填写 rel=2 的双方资料并保存验证。
2. 之后进入 AI Provider / API Key 加密存储模块，再接 3×3 快速回复（Context 读取 chat_message 当前 content + Profile）。
3. 媒体解析、Timeline、Deep Analysis 继续后置。
