---
name: cowagent
description: Adapts CowAgent repository for local reference and usage guidance. Invoke when user asks to use CowAgent capabilities, architecture, or deployment workflow.
---

# CowAgent

这是 `CowAgent` 的本地适配技能。

作用：
- 保留上游源码用于查阅与二次开发
- 提供中文口令和快速使用路径
- 需要时按当前项目场景给出接入建议

## 中文口令

你可以直接这样说：

- 用 CowAgent 思路帮我规划这个任务
- 按 CowAgent 的能力给我做部署清单
- 参考 CowAgent 的技能系统，帮我设计一个技能
- 帮我梳理 CowAgent 的长期记忆和知识库机制

## 适用场景

- 想了解 CowAgent 的架构与能力边界
- 想参考 CowAgent 的技能系统设计
- 想在项目里借鉴其任务规划、记忆、工具调用机制
- 想做接入评估（渠道、模型、运行方式）

## 本地路径

- 上游源码：`codeworkfiles/cowagent-source/`
- 关键文档：`codeworkfiles/cowagent-source/README.md`
- 内置技能示例：`codeworkfiles/cowagent-source/skills/`

## 执行规则

1. 先明确用户目标：是“了解”、 “接入评估”还是“落地改造”
2. 到本地源码中读取对应模块和文档
3. 输出结构化建议：可行方案、依赖前置、风险与验证步骤
4. 未经用户确认，不直接改造当前业务系统

## 说明

- `CowAgent` 仓库本身不是 Trae 原生单技能仓库
- 当前安装方式为“上游源码保留 + Trae 适配入口”
