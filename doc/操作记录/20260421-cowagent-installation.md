# CowAgent 安装记录

## 背景

用户提供仓库：`https://github.com/zhayujie/CowAgent.git`，要求安装。

## 仓库识别结果

`CowAgent` 是完整的 Agent 框架仓库，不是 Trae 原生单技能仓库。

仓库包含：
- Agent 核心模块（任务规划、记忆、知识库、工具系统）
- 多渠道接入能力（如微信、飞书、钉钉、网页等）
- 自身 Skills 目录和 CLI 能力

## 本次安装策略

采用“两层安装”：

1. 上游源码保留  
- `codeworkfiles/cowagent-source/`

2. Trae 适配技能  
- `doc/技能库/cowagent/SKILL.md`  
- `.trae/skills/cowagent/SKILL.md`

## 结果

- 已完成上游源码克隆并可本地查阅
- 已完成 `cowagent` 技能入口安装
- 已补充简洁中文口令示例，支持中文直接调用

## 使用方式

- 切换到 CowAgent 方案，帮我规划这个任务
- 参考 CowAgent 的技能系统，帮我设计一个技能
- 按 CowAgent 的能力给我做部署清单

## 说明

本次为“技能接入与参考层安装”，未直接启动 CowAgent 服务进程，也未改动当前业务项目运行配置。
