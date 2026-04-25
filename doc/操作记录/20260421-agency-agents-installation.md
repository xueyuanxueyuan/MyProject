# agency-agents 安装记录

## 背景

用户提供仓库：

- `https://github.com/msitarzewski/agency-agents.git`

## 仓库识别结果

`agency-agents` 并不是 Trae 原生 `SKILL.md` 仓库，而是一整套面向 Claude Code 的代理人格 Markdown 集合。

其特点：

- 代理文件使用 `.md + YAML frontmatter`
- 面向 Claude Code 的 `.claude/agents/` 使用方式
- 支持按分组安装整类代理，如 `engineering/*.md`
- 用户通过点名代理名称来激活对应专家人格

## 本次安装策略

采用“两层安装”：

### 1. 原始代理库

完整上游源码已克隆到：

- `codeworkfiles/agency-agents-source/`

该目录保留原始代理文件，便于后续按需读取、扩展或升级。

### 2. Trae 适配层

新增一个本地适配技能：

- `doc/技能库/agency-agents/SKILL.md`
- `doc/技能库/agency-agents/AGENTS_CATALOG.md`
- `.trae/skills/agency-agents/SKILL.md`

该技能的作用是：

- 在当前 Trae 工作区中统一接入上游代理人格库
- 用户点名代理时，到本地源码库中读取对应 `.md`
- 提取角色规则、交付标准和工作流后再执行任务

## 当前已可用的典型代理

### Engineering

- `Frontend Developer`
- `Backend Architect`
- `Code Reviewer`
- `Codebase Onboarding Engineer`
- `Minimal Change Engineer`
- `Software Architect`
- `Technical Writer`

### Testing

- `Reality Checker`
- `Evidence Collector`
- `API Tester`
- `Accessibility Auditor`
- `Performance Benchmarker`

### Product / Project / Specialized

- `Product Manager`
- `Senior Project Manager`
- `Agents Orchestrator`
- `Workflow Architect`
- `MCP Builder`

## 安装边界

### 已完成

- 克隆原始仓库源码
- 在当前工作区安装 `agency-agents` 适配技能
- 补充代理目录索引和安装留痕

### 未做

- 没有把上游数百个代理文件逐个改写成 Trae 原生独立技能
- 没有对每个代理单独创建 `.trae/skills/<agent-name>/SKILL.md`

## 原因

上游仓库本质上是“代理人格库”，不是“Trae 技能库”。如果逐个改写成独立技能，会带来较大维护成本，也不利于后续同步上游更新。

因此当前优先采用：

- 保留上游原貌
- 增加一个统一适配层

## 后续建议

如果后续你高频使用某几个代理，可以继续拆出独立技能，例如：

- `frontend-developer`
- `backend-architect`
- `code-reviewer-agency`
- `reality-checker-agency`
