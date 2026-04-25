# planning-with-files 技能安装记录

## 背景

用户要求安装 `https://github.com/OthmanAdi/planning-with-files.git` 技能。

## 处理结果

- 已新增技能主源文件：`doc/技能库/planning-with-files/SKILL.md`
- 已新增 Trae 技能入口：`.trae/skills/planning-with-files/SKILL.md`
- 已生成本次安装留痕

## 特殊情况

- 本次通过 `git clone` 直连 GitHub 时失败，错误为网络连接被对端重置
- 因此未直接从仓库完整克隆本地源码
- 改为依据公开可见的技能说明页与仓库可检索信息，安装一个可用版 `planning-with-files`

## 安装内容

### 1. 技能定位

`planning-with-files` 是单技能仓库，核心目标是：

- 用 `task_plan.md` 跟踪阶段、决策与错误
- 用 `findings.md` 记录研究发现
- 用 `progress.md` 记录会话执行过程

### 2. 导入方式

- `doc/技能库/planning-with-files/SKILL.md` 保存完整可用正文
- `.trae/skills/planning-with-files/SKILL.md` 提供 Trae 可发现入口

### 3. 技能关键规则

- 复杂任务先建计划文件
- 每 2 次查看/搜索/浏览操作后，及时把关键信息落盘
- 重大决策前先重读计划文件
- 所有错误都要记录
- 不重复同一失败动作
- 连续 3 次失败后升级给用户

## 验证结果

- `doc/技能库/planning-with-files/SKILL.md` 已创建
- `.trae/skills/planning-with-files/SKILL.md` 已创建
- 未发现新增诊断错误

## 后续建议

- 若后续网络恢复，可再把源仓库完整克隆到 `codeworkfiles` 做一次正文和辅助脚本补全
- 如果你要，我也可以继续为这个技能补一套本仓库适配版模板文件，例如 `task_plan.md`、`findings.md`、`progress.md` 初始化模板
