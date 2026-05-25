# agency-agents-zh 技能同步记录

## 背景

用户要求依据 `https://github.com/jnMetaCode/agency-agents-zh.git` 更新本地 `agency-agents` 对应技能。

## 同步依据

- 外部参考仓库：`agency-agents-zh`
- 重点参考文件：
  - `README.md`
  - `CATALOG.md`
  - `strategy/coordination/agent-activation-prompts.md`
  - `specialized/agents-orchestrator.md`

## 发现的问题

- 本地 `agency-agents` 技能仍是早期简化版，只覆盖少量角色
- 文档中引用的 `codeworkfiles/agency-agents-source/` 路径当前并不存在
- 缺少对 `agency-agents-zh` 当前规模、部门分类和中国场景原创角色的说明
- 缺少 Trae 场景下“精选安装、显式点名、少量 alwaysApply”的使用建议

## 本次更新

### 1. 技能主文档升级

更新 `doc/技能库/agency-agents/SKILL.md`：

- 将技能定位从“简单口令说明”升级为“选角与角色适配指南”
- 同步 `agency-agents-zh` 的整体规模信息：`215` 个角色、`18` 个部门、`50` 个中国市场原创角色
- 增补选角流程、Trae 使用要点、常用角色分组和中文口令示例
- 删除失效的本地源码路径说明

### 2. 速查表重写

更新 `doc/技能库/agency-agents/AGENTS_CATALOG.md`：

- 改为“任务类型 -> 主推荐角色 -> 备选角色 -> 适用说明”的速查结构
- 增补 `最小变更工程师`、`MCP 构建器`、`飞书集成开发工程师`、`政务数字化售前顾问` 等角色
- 增加 Trae 使用提示，强调精选安装和显式点名

### 3. Trae 实际技能镜像同步

更新 `.trae/skills/agency-agents/SKILL.md`：

- 让 Skill 工具实际加载到的内容与文档主源保持一致
- 保持更简洁，但保留角色推荐、Trae 约束和常用角色分组

## 本次涉及文件

- `doc/技能库/agency-agents/SKILL.md`
- `doc/技能库/agency-agents/AGENTS_CATALOG.md`
- `.trae/skills/agency-agents/SKILL.md`
- `doc/操作记录/20260521-agency-agents-zh-技能同步.md`

## 结果

本地 `agency-agents` 技能已从“少量常用代理说明”升级为基于 `agency-agents-zh` 的本地化选角技能，更适合在 Trae 工作流中按任务选择角色并显式切换视角。
