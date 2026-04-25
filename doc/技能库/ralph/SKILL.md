---
name: ralph
description: Convert PRDs to prd.json format for the Ralph autonomous agent system. Use when you have an existing PRD and need to convert it to Ralph JSON.
---

# Ralph PRD Converter

把已有 PRD 转换成 `ralph` 可执行的 `prd.json` 格式，供自主迭代代理循环按故事逐项推进。

## 中文口令

你可以直接用中文这样说：

- 把这个 PRD 转成 `prd.json`
- 把需求文档转换成 Ralph 的任务清单（`prd.json`）
- 只做 PRD 转换，不要开始实现：{PRD 文件路径}

## Ralph 执行器

当前工作区已安装 Ralph 循环执行器文件：

- `scripts/ralph/ralph.sh`
- `scripts/ralph/prompt.md`
- `scripts/ralph/CLAUDE.md`
- `scripts/ralph/prd.json.example`

## 何时使用

适用于：
- 已经有 Markdown PRD，需要转成 `prd.json`
- 用户要求“转成 ralph 格式”“生成 prd.json”“转换为 Ralph JSON”
- 需要为 Ralph 自主执行循环准备结构化故事列表

不适用于：
- 需求还没整理成 PRD
- 直接开始实现，不需要 Ralph 流程

## 输出目标

生成的 JSON 应包含：

- `project`
- `branchName`
- `description`
- `userStories`

每个 `userStories` 条目应包含：

- `id`
- `title`
- `description`
- `acceptanceCriteria`
- `priority`
- `passes`
- `notes`

## 核心规则

### 1. 故事必须足够小

每条用户故事必须能在 **一次 Ralph 迭代** 中完成。

合适的故事大小示例：
- 新增一个数据库字段与迁移
- 给现有页面增加一个过滤器
- 增加一个独立 UI 组件
- 修改一个后端动作逻辑

过大的故事必须拆分，例如：
- “做完整个仪表盘”
- “增加认证系统”
- “重构整个 API”

### 2. 依赖顺序优先

故事执行按优先级顺序进行，因此必须保证前置依赖在前：

- 先 schema / 数据库
- 再后端逻辑
- 再 UI 组件
- 最后汇总视图或聚合页面

### 3. 验收标准必须可验证

好的标准：
- `Add status column to tasks table with default 'pending'`
- `Filter dropdown has options: All, Active, Completed`
- `Typecheck passes`

不好的标准：
- `Works correctly`
- `Good UX`
- `Handles edge cases`

### 4. 每条故事默认状态

- `passes: false`
- `notes: ""`

### 5. 通用质量门禁

每条故事至少追加：
- `Typecheck passes`

如果有可测试逻辑，还应加：
- `Tests pass`

如果涉及 UI，还应加：
- `Verify in browser using dev-browser skill`

## 转换要求

- 每个用户故事转成一个 JSON 条目
- `id` 采用顺序编号，例如 `US-001`
- `priority` 按依赖顺序和文档顺序生成
- `branchName` 从功能名派生，建议前缀 `ralph/`
- 输出文件名为 `prd.json`

## 约束

- 不实现功能
- 不跳过拆分大故事
- 不输出模糊验收标准
- 不生成无法按顺序执行的故事列表
