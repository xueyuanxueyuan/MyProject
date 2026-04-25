---
name: agency-agents
description: Adapts the agency-agents persona library for local use. Invoke when you want me to work in a specific expert agent style such as Frontend Developer, Backend Architect, Code Reviewer, or Reality Checker.
---

# Agency Agents

这是一个“代理人格库适配技能”。

作用很简单：
- 你点名一个代理
- 我切到那个代理的视角做事

本地代理库位置：
- `codeworkfiles/agency-agents-source/`

## 怎么用

你直接这样说就行：

- 切换到前端开发代理，看看这个页面
- 切换到后端架构代理，看看这个接口设计
- 切换到代码评审代理，审一下这段代码
- 切换到严格验收代理，验收这个功能
- 切换到流程编排代理，帮我梳理流程

## 常用代理

- 前端开发代理：`Frontend Developer`
- 后端架构代理：`Backend Architect`
- 代码评审代理：`Code Reviewer`
- 严格验收代理：`Reality Checker`
- 仓库讲解代理：`Codebase Onboarding Engineer`
- 流程编排代理：`Agents Orchestrator`

## 我会怎么做

1. 识别你指定的代理
2. 读取本地对应代理文档
3. 按那个代理的风格和标准继续完成任务

## 没指定代理时

我会先给你推荐最合适的几个，例如：

- 页面问题 -> 前端开发代理
- 接口/数据库 -> 后端架构代理
- 代码审查 -> 代码评审代理
- 功能验收 -> 严格验收代理
- 流程设计 -> 流程编排代理

## 中文口令对照

- 前端开发代理 -> `Frontend Developer`
- 后端架构代理 -> `Backend Architect`
- 代码评审代理 -> `Code Reviewer`
- 严格验收代理 -> `Reality Checker`
- 仓库讲解代理 -> `Codebase Onboarding Engineer`
- 流程编排代理 -> `Agents Orchestrator`

## 补充说明

- 这不是把上游所有代理逐个改造成 Trae 技能
- 上游原始代理文件保留在 `codeworkfiles/agency-agents-source`
- 如果你后续常用某几个代理，我可以再单独拆成独立技能
