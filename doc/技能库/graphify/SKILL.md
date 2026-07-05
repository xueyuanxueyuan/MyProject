---
name: "graphify"
description: "Uses Graphify to build and query knowledge graphs from code, docs, diagrams, and mixed corpora. Invoke when the user asks to install Graphify or analyze a folder with Graphify."
---

# Graphify

## 用途

`Graphify` 是一个公开的知识图谱技能，可将代码、文档、图片、论文等混合资料转换为可查询的知识图谱，产出交互式图谱页、结构化 JSON 与分析报告。

## 触发场景

- 用户明确要求安装、启用或使用 `Graphify skill`
- 用户希望把某个目录构建成知识图谱，而不是逐文件检索
- 用户希望基于知识图谱回答“架构怎么串起来”“两个概念如何关联”“哪些节点最关键”等问题
- 用户希望对代码、文档、设计资料做跨文件关联分析

## 本项目安装结论

当前工作区已采用本地桥接方式安装 `Graphify`：

- 主源文档：`doc/技能库/graphify/SKILL.md`
- Trae 本地入口：`.trae/skills/graphify/SKILL.md`

这样可先让 Trae 在当前仓库中发现并加载 `Graphify` 技能说明，而不依赖全局目录。

## 运行前提

- `Graphify` 官方运行时依赖 Python 3.10+
- 推荐使用 `uv` 安装官方包 `graphifyy`
- PowerShell 中运行命令时使用 `graphify .`，不要写 `/graphify .`

## 官方运行时安装

若后续需要真正执行 `Graphify` CLI，可在具备 Python 环境后使用：

```powershell
uv tool install graphifyy
graphify install
```

若希望走项目级安装，可优先使用：

```powershell
graphify install --project
```

## 本地补充

- 当前机器检查结果显示：`python`、`uv`、`pipx` 均未就绪，因此本次未继续安装官方 CLI 运行时
- 为避免直接改写未知的全局技能目录，本次仅在当前仓库内完成 `Graphify` 技能入口安装
- 后续若补齐 Python/uv，可再执行官方 CLI 安装，必要时同步评估其对 `AGENTS.md` 等常驻指令文件的影响

## 主源位置

- `doc/技能库/graphify/SKILL.md`
- `.trae/skills/graphify/SKILL.md`
- `doc/操作记录/20260705-Graphify-skill安装记录.md`
