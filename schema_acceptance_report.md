# 数据库结构验收报告（只读检查，未改 SQL/Java，未导入数据）

> 依据：V1__init.sql + MySQL information_schema 实测（schema_check.txt / flyway_migration_report.txt）。

## A. 已符合设计的部分

1. **relationship（关系根节点）✓**
   - 一对象一行，BIGINT 自增主键，status(ACTIVE/ARCHIVED/DELETED)、goal_note 齐全。
   - 阶段历史由 `relationship_stage_history` 落地（stage + started_at/ended_at，FK 级联），覆盖初识/暧昧/恋爱中/分手/重新联系（枚举 5 值与 DDL 注释一致）。

2. **chat_message ✓**
   - relationship_id 外键 → relationship.id；message_time DATETIME；content MEDIUMTEXT。
   - sender_type（代码枚举 ME/OTHER/SYSTEM，列宽 VARCHAR(10) 容纳 SYSTEM）、message_type 14 值。
   - 来源可区分：source_type 六值（IMPORTED/USER_ORIGINAL/USER_ADDED/AI_GENERATED/AI_GENERATED_EDITED/UNKNOWN），并保留 source_message_id、source_hash、source_content、source_ai_message_id、metadata JSON。

3. **chat_message_revision ✓**
   - editMessage 流程是"先 INSERT revision 再 UPDATE 主表"；导入原文固定在 source_content，编辑只改 content——**原始版本不会被覆盖**。

4. **ai_generated_message ✓**
   - original_text 保存 AI 最初完整文本；context_id/strategy/status(GENERATED/SELECTED/SENT/ABANDONED)。
   - 与最终消息的关系：chat_message.source_ai_message_id 回指其 id（软关联）。

5. **ai_message_edit_analysis ✓**
   - 双外键：ai_message_id→ai_generated_message、final_message_id→chat_message；
   - similarity_score、modification_rate、modification_type、algorithm_version 齐备，修改比例/审计数据已预留。

6. **chat_import_record ✓**
   - importer_type + source_location 可区分不同备份来源；total/inserted/duplicate/conflict 计数与状态(PREVIEWING/CONFIRMED/FAILED)。

7. **profile / profile_item ✓**
   - profile 按 (relationship_id, owner_type) 唯一，我的/对方各一条；
   - profile_item 带 source_type(USER/AI/IMPORT)、confidence、usage_weight、status；
   - AI 原始判断独立存 ai_profile_observation，与用户确认值物理分离（"AI 不覆盖用户确认"有结构支撑）。

8. **timeline_event ✓**
   - event_type/title/description/event_time、source_type(USER/AI)、status(PROPOSED/CONFIRMED)，AI 事件必须确认的规则可落地。

9. **uk_chat_source 冲突场景逐项检查（重点）✓**
   定义实测为 UNIQUE(relationship_id, source_type, source_message_id)：
   - 用户改消息：不改 source 三元组 → **不冲突**。
   - 重复导入同一备份：三元组相同 → existsBySource 预查 + 唯一约束双保险，自动跳过 → **这正是幂等所需**。
   - 用户手动添加：source_type=USER_ADDED，三元组不同；且 source_message_id 可空，**InnoDB 唯一索引允许多个 NULL** → 不冲突。
   - AI 发送：source_type=AI_GENERATED(_EDITED)，三元组不同 → 不冲突。

## B. 与技术设计存在的差异

1. **relationship 无 relationship_type 列**：无法在结构上标注 ROMANTIC；当前计划写入 goal_note 备注。也无 partner_wxid 列（对方微信 id 没有专门字段）。
2. sender_type 列注释仍写 'ME/OTHER'（代码已含 SYSTEM）——仅注释陈旧，功能无影响。
3. chat_message.source_ai_message_id **无数据库外键**（软关联；设计可接受，但数据库不保证引用完整性）。
4. profile_item 无 (profile_id, category, item_key) 唯一约束——重复条目靠应用层防范。
5. chat_import_record 无备份校验和/指纹列——仅靠 source_location 路径区分来源，路径改名即视为新来源。
6. ai_message_edit_analysis 对 ai_message_id 唯一：同一 AI 候选若被发送两次，无法各记一条（边缘场景）。
7. 无媒体相关表（本阶段明确推迟，属预期）。

## C. 可能影响后续正式导入的风险

1. **幂等键稳定性**：source_message_id = `md5(wxid):localId`。若以后重新导出导致 local_id 重新编号，再导入会被当成新消息插入（不报错但产生重复）。当前这份备份内 localId 稳定，风险可控。
2. **跨来源重复**：同一关系若先导入 HTML 再导入 SQLite，source_type 不同，唯一约束不拦截，会产生逻辑重复（需应用层按时间/内容再判）。
3. **时间口径**：message_time 存的是 Asia/Shanghai 墙钟 DATETIME，JDBC 连接已固定 serverTimezone=Asia/Shanghai；以后 JVM 改时区不会改写存量值，但展示层必须始终按 UTC+8 解读。
4. 导入是单线程顺序执行；existsBySource 预查与唯一约束即使在并发下也有兜底（抛错计入失败），本场景无并发。

## D. 正式导入前必须修改的问题

**无强制修改项。** 结构已能支撑：708 条写入、幂等去重、source_message_id 追溯、ME/OTHER/SYSTEM 分类、Raw/Effective 分离。

B 节差异均不阻断导入；relationship_type 用 goal_note 备注的方案可接受。若后续希望在结构上正式体现 relationship_type / partner_wxid，应在导入完成后新增 **V2__xxx.sql**（不得改已发布的 V1）。

---
结论：结构验收通过，可在你确认后开始 708 条消息正式导入。
