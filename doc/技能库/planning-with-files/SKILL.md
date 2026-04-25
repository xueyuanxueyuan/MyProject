---
name: planning-with-files
description: Implements Manus-style file-based planning to organize and track progress on complex tasks. Use when asked to plan, break down, or organize multi-step work or research.
---

# Planning with Files

用持续落盘的 Markdown 文件充当“磁盘工作记忆”，把复杂任务拆解、研究记录和会话进度保存下来，避免上下文丢失。

## 何时使用

适用于：
- 多步骤任务
- 研究型任务
- 跨多个工具调用的复杂工作
- 需要中断恢复、跨会话续做或持续跟踪的任务
- 需要明确阶段、记录发现、追踪错误和决策的工作

不适用于：
- 简单问答
- 单文件小改动
- 一两步即可完成的快速查询

## 核心思想

- 上下文窗口像内存，短暂且有限
- 文件系统像磁盘，持久且可复用
- 任何重要信息都要及时写入文件，而不是只停留在对话里

## 必备文件

规划文件应创建在**项目根目录**，而不是技能安装目录：

1. `task_plan.md`
   - 记录目标、阶段、状态、决策、错误
2. `findings.md`
   - 记录研究结论、发现、技术信息
3. `progress.md`
   - 记录当前会话执行过程、测试结果、已完成动作

## 工作规则

### 1. 先建计划

复杂任务开始前，必须先创建 `task_plan.md`。

### 2. 2-Action Rule

每进行 2 次查看、搜索、浏览器查看、资料检查后，立刻把关键发现写入 `findings.md`。

### 3. 决策前先读

做重大决策前，重新阅读 `task_plan.md`，把目标和剩余阶段重新拉回注意力窗口。

### 4. 每阶段完成后更新

阶段推进后需要同步更新：
- `task_plan.md`：状态从 `pending` / `in_progress` 更新到最新值
- `progress.md`：记录本轮完成内容
- `findings.md`：补充新发现

### 5. 记录所有错误

所有错误都要写入 `task_plan.md` 或 `progress.md`，避免重复踩坑。

### 6. 不要重复失败动作

如果一个动作已经失败，下一次必须改变方法、工具或路径，不能原样重试。

### 7. 三次失败后升级

- 第 1 次：读错误并做定向修复
- 第 2 次：更换思路、工具或方法
- 第 3 次：重新审视假设和整体方案
- 仍失败：向用户汇报已尝试内容并请求指导

## 文件模板

### `task_plan.md`

```markdown
# Task Plan

## Goal
- [填写任务目标]

## Phases
| Phase | Description | Status |
|-------|-------------|--------|
| 1 | 调研与理解 | pending |
| 2 | 方案确认 | pending |
| 3 | 执行与验证 | pending |

## Decisions
| Time | Decision | Reason |
|------|----------|--------|

## Errors Encountered
| Error | Attempt | Resolution |
|-------|---------|------------|
```

### `findings.md`

```markdown
# Findings

## Key Discoveries
- [记录关键发现]

## References
- [记录参考资料、结论和限制]

## Open Questions
- [记录待确认问题]
```

### `progress.md`

```markdown
# Progress

## Session Log
- [时间] 已完成：
- [时间] 当前问题：
- [时间] 下一步：

## Validation
- [记录测试、验证或检查结果]
```

## 读写决策矩阵

| 场景 | 动作 | 原因 |
|------|------|------|
| 刚写完文件 | 不立即回读 | 内容仍在当前上下文 |
| 看完网页、图片、文档 | 立刻写入 findings | 防止视觉信息丢失 |
| 开始新阶段 | 先读 plan / findings | 重新对齐目标 |
| 出现错误 | 先读相关计划文件 | 确认当前状态和已尝试动作 |
| 长时间中断后恢复 | 先读全部规划文件 | 恢复现场 |

## 反模式

不要这样做：
- 只在对话里说目标，不建文件
- 出错后静默重试，不记录失败
- 在上下文里堆大量临时信息，不落盘
- 复杂任务先执行再补计划
- 反复重复同一失败动作

改为这样做：
- 先建 `task_plan.md`
- 研究过程写入 `findings.md`
- 执行进度写入 `progress.md`
- 重大决策前重读计划
- 错误显式记录，下一次换方法

## 会话恢复检查

恢复任务时，先确认以下 5 个问题：

1. 我现在在哪个阶段？
2. 剩余阶段还有哪些？
3. 当前目标是什么？
4. 我已经发现了什么？
5. 我已经做了什么？

如果这 5 个问题不能快速回答，就先读：
- `task_plan.md`
- `findings.md`
- `progress.md`
