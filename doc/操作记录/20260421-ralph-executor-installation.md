# Ralph 执行器安装记录

## 背景

用户再次提供仓库：`https://github.com/snarktank/ralph.git`，期望把 Ralph 的“可跑执行框架”也接入当前工作区。

## 本次安装结果

### 1) 保留上游源码（便于对照与追溯）

- `codeworkfiles/ralph-source/`

### 2) 安装 Ralph 执行器（可运行资产）

已复制到：

- `scripts/ralph/ralph.sh`
- `scripts/ralph/prompt.md`
- `scripts/ralph/CLAUDE.md`
- `scripts/ralph/prd.json.example`
- `scripts/ralph/README.zh-CN.md`

并已设置 `scripts/ralph/ralph.sh` 为可执行文件。

## 同步调整

为满足“使用口令中文化”，已在以下技能中补充中文口令示例：

- `doc/技能库/prd/SKILL.md`
- `doc/技能库/ralph/SKILL.md`
- `.trae/skills/prd/SKILL.md`
- `.trae/skills/ralph/SKILL.md`

## 使用方式

### 中文口令（对我说）

- 帮我写一份需求文档（PRD），主题是：{功能一句话}
- 把这个 PRD 转成 `prd.json`

### 运行执行器（命令行）

在仓库根目录运行：

```bash
./scripts/ralph/ralph.sh 10
```

## 说明

Ralph 自主循环运行依赖外部 AI 工具（Amp 或 Claude Code）与本机鉴权环境，本次只把“执行器与模板文件”接入当前仓库，不负责安装这些外部工具。
