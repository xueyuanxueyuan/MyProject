# Hermes Kanban / 会话数据恢复尝试记录

- 日期：2026-06-02
- 背景：`rm -rf ~/.hermes` 误删后尝试恢复 Kanban 任务历史与会话记录

## 1. 完整数据库恢复（kanban.db / state.db）

| 尝试项 | 结果 |
|--------|------|
| 回收站 | 空（`rm -rf` 未进 Trash） |
| 全机扫描 kanban.db / state.db 副本 | 仅发现重装后的空库 |
| cc-switch `db_backup_*.db` | 为 cc-switch 自身库，不含 Hermes 数据 |
| extundelete（ext4） | **需 sudo 密码**，Agent 环境无法执行 |
| Timeshift / deja-dup | 未发现可用快照 |

**结论：完整 Kanban + 会话正文目前无法在本环境自动恢复。**

若仍要赌磁盘恢复，请在**尽量少写盘**后于本机终端执行：

```bash
sudo bash /home/source/Jetbrains/Probject/Gjj/scripts/recovery/hermes-extundelete-recover.sh
```

脚本会尝试从 `/dev/nvme0n1p2` 恢复 `home/source/.hermes/kanban.db`、`state.db` 等；输出目录默认为 `~/hermes-recovery-<时间戳>/RECOVERED_FILES/`。

## 2. 部分元数据恢复（tirith 审计日志）

来源：`~/.local/share/tirith/log.jsonl`（命令审计，非 Hermes 官方备份）

| 指标 | 数量 |
|------|------|
| Kanban 相关命令 | 103 条 |
| 可识别任务 ID | 25 个 |
| 可识别 create 标题 | 33 条（含重复创建） |
| 看板 | `jshs-v3`（结算系统3.0开发） |

产物路径：

- JSON 清单：`doc/操作记录/20260602-Hermes-Kanban-tirith恢复清单.json`
- 副本：`~/.hermes/recovery/tirith-kanban-manifest.json`

**限制：**

- 不含任务正文、评论、依赖关系、会话 messages
- `complete --summary` 等字段多数被 tirith redact
- 无法映射「标题 → 原 task_id」（create 命令未保留返回 ID）

## 3. 其他零星线索

- Gjj 设计文档：`t_1b183d56`、`t_1b22634a`（R71/R70 复核任务）
- `journalctl --user -u hermes-gateway`：极少量 kanban 警告，无完整数据

## 4. extundelete 结果

用户反馈 **extundelete 失败**，完整 DB 无法恢复。

## 5. 看板骨架重建（2026-06-02）

已执行 `scripts/recovery/rebuild-jshs-v3-kanban.py`，从 JSON 清单在 `jshs-v3` 看板重建 **16** 条任务：

| 分组 | 状态 | 数量 |
|------|------|------|
| T1–T3 领域/基础设施 | done | 3 |
| T4.1–T6.3 历史开发卡 | archived | 10 |
| R69 复核 | done | 1 |
| R70/R71 复核 | ready（待继续） | 2 |

幂等键：`jshs-v3-rebuild-{T1|T2|…|R71-0}`，重复执行不会重复建卡。

**仍不可恢复：** 原 task_id、评论、依赖边、会话 messages、工作区 scratch 内容。

## 6. 后续建议

1. 定期备份：

```bash
tar czf ~/hermes-backup-$(date +%Y%m%d).tar.gz \
  -C ~ .hermes/kanban.db .hermes/state.db .hermes/sessions .hermes/kanban
```
