# Ralph 技能安装记录

## 背景

用户提供仓库：`https://github.com/snarktank/ralph.git`

经识别，`ralph` 仓库不是单一技能，而是围绕 Ralph 自主代理循环提供两项核心技能：

- `prd`
- `ralph`

其中：
- `prd` 用于生成结构化 PRD
- `ralph` 用于把 PRD 转换为 `prd.json`，供 Ralph 循环执行

## 本次安装结果

### 技能主源

已新增：

- `doc/技能库/prd/SKILL.md`
- `doc/技能库/ralph/SKILL.md`

### Trae 技能入口

已新增：

- `.trae/skills/prd/SKILL.md`
- `.trae/skills/ralph/SKILL.md`

## Ralph 仓库的关键能力说明

Ralph 的核心并不只是技能文件，还包括一个自主循环模式：

- 每轮用全新上下文实例执行
- 记忆通过 `git history`、`progress.txt` 和 `prd.json` 持久化
- 每轮只处理一个 `passes: false` 的高优先级故事
- 通过质量检查后提交并更新状态
- 反复迭代直到全部故事完成

本次按照“安装技能”的请求，仅落地了可在当前 Gjj 工作区直接使用的技能部分，没有额外引入 `ralph.sh`、`prompt.md`、`CLAUDE.md` 等循环脚本资产。

## 安装边界

### 已安装

- `prd` 技能
- `ralph` 技能

### 未安装

- `scripts/ralph/ralph.sh`
- `progress.txt` / `prd.json` 示例资产
- Ralph 自主循环运行脚本
- Claude Code Marketplace 插件清单

## 原因

当前用户请求更贴近“安装这个仓库里的技能”，因此优先安装技能本体，而不是把完整执行框架直接植入当前项目。

## 后续建议

如果后续要把 Ralph 的“自主循环执行器”也接入当前工作区，可继续补装：

1. `scripts/ralph/ralph.sh`
2. Claude prompt 模板
3. `prd.json` 示例与任务目录
4. 针对 Gjj 项目的本地适配说明
