---
name: anti-laziness-anti-hallucination
description: Use when starting or executing substantive work where unsupported claims, placeholder delivery, skipped verification, or over-trusting memory are likely.
---

# Anti Laziness And Anti Hallucination

## Overview

这个技能用于约束智能体在实质性任务中保持“先查证、再实施、再验证、后交付”的闭环，防止出现偷懒式交付、口头通过、凭记忆编造、把未完成工作甩给用户等问题。

核心原则只有三条：

- 证据优先于感觉
- 完整交付优先于占位交付
- 真实状态优先于好听表述

## When To Use

以下场景默认应触发本技能：

- 新功能、缺陷修复、重构、脚本编写、规则编写
- 需要搜索上下文、评估影响面、输出多文件改动
- 需要声称“已完成”“已修复”“已通过”“已验证”
- 需要委派子智能体、引用历史结论、依赖命令执行结果
- 用户需求较短、较模糊，但交付物必须真实完整

以下场景可不强制整套执行，但仍应遵守“如无证据不下结论”：

- 简短闲聊
- 纯概念解释
- 明确只要求头脑风暴且尚未进入实现

## Default Adoption

在支持“规则 + 技能”双机制的代理环境中，推荐把本技能设为“实质性任务默认启用”的基础能力：

- 命中开发、排障、评审、规则编写、脚本编写、多步骤分析时自动启用
- 命中最终交付前自动联动 `verification-before-completion`
- 命中新增功能或缺陷修复时继续叠加 `test-driven-development`

这意味着用户不需要每次显式说“启用防偷懒模式”，代理也应主动进入该闭环。

## Hard Gates

### 1. Context Gate

开始动手前，必须先确认客观上下文，而不是直接凭记忆作答：

- 先读相关 `doc`、规则、技能、目标文件
- 不确定的目录、依赖、接口、类名、脚本入口，必须先查
- 涉及现有代码改动时，先看当前实现，再谈方案

### 2. Planning Gate

进入实质性工作后，必须先把任务拆成可验证的步骤：

- 使用 `TodoWrite` 拆分 3 步以上子任务
- 明确每一步的完成证据是什么
- 写代码或改规则前，先想清楚最终如何验证

### 3. Delivery Gate

交付物必须完整，禁止留下偷懒占位：

- 禁止 `TODO`、`...`、`其余保持不变`、`后续补充`
- 禁止只给伪代码、骨架代码、空实现、口头承诺
- 禁止把本应由智能体完成的实现、排查、验证转嫁给用户

### 4. Verification Gate

任何成功性表述都必须有最新证据支撑：

- 想说“已完成”，先确认变更已落盘且结果已检查
- 想说“已修复”，先验证原问题路径
- 想说“测试通过/构建通过”，先运行真实命令并核对退出码
- 想说“子任务完成”，不能只信任子智能体回报，必须复核结果

### 5. Honesty Gate

无法确认时，只能说真实状态：

- 可以说“未验证”
- 可以说“无此数据”
- 可以说“当前仅完成扫描，尚未完成构建验证”
- 不可以说“应该没问题”“理论上可行”“大概率通过”

## Minimal Workflow

1. 读取与任务直接相关的规则、文档、代码或工具说明
2. 通过 `TodoWrite` 拆解步骤，并确定验证方式
3. 实施最小必要改动，避免无关格式漂移
4. 对改动结果执行最小充分验证
5. 如仓库内已有改动，运行本地守卫脚本扫描占位符与偷懒痕迹
6. 只基于已验证事实向用户汇报结果

## Local Guardrail

本仓库配套脚本：

- `scripts/guardrails/agent-delivery-guardrail.sh`

常用方式：

```bash
# 只扫描本次改动文件中的偷懒痕迹
scripts/guardrails/agent-delivery-guardrail.sh --scan-only

# 只扫描指定文件
scripts/guardrails/agent-delivery-guardrail.sh --scan-only path/to/file1 path/to/file2

# 扫描后执行指定验证命令
scripts/guardrails/agent-delivery-guardrail.sh --verify-command "npm run test"

# 扫描后按仓库类型自动选择验证命令
scripts/guardrails/agent-delivery-guardrail.sh --auto-verify
```

## Related Skills

- `verification-before-completion`：约束“无验证不宣告完成”
- `test-driven-development`：约束“无失败测试不写生产代码”
- `writing-skills`：约束“新技能先压测基线再发布”

## Common Failure Patterns

| 违规说法 | 问题本质 | 正确做法 |
|----------|----------|----------|
| “我已经改好了” | 未给证据 | 给出文件变更与验证结果 |
| “应该可以通过” | 预测替代验证 | 运行命令再汇报 |
| “其余逻辑保持不变” | 省略关键实现 | 写出真实变更或明确未改动依据 |
| “用户可自行补充” | 转嫁工作 | 先把该做的做完 |
| “子 agent 说成功了” | 盲信代理结果 | 复核 diff、日志、命令输出 |

## Red Flags

出现下列念头时，应立即停止并回到查证流程：

- “这次就不跑验证了”
- “我记得目录是这样的”
- “先口头回复，后面再补”
- “先写占位，等用户再说”
- “虽然没跑，但大概率没问题”

## Final Rule

如果没有最新证据，就不要输出成功结论。

如果交付物不完整，就不要伪装成已完成。
