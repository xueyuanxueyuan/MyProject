---
name: "cli-anything-local-tools"
description: "Builds agent-usable bridges for local software and invokes native executables safely. Invoke when the user wants to control local desktop tools, CLIs, or installed software through Trae."
---

# CLI Anything Local Tools

## 用途

`cli-anything-local-tools` 是参照 `CLI-Anything` 思路，为当前 Trae 工作区定制的“本机软件桥接技能”。

它的目标不是复制外部项目的完整插件生态，而是在 **Trae + RunCommand + 本地技能文档** 这一现实能力边界内，实现尽可能接近的效果：

- 让代理优先通过 **本机可执行程序、原生命令行、无头模式、脚本接口、本地 API / MCP 桥接** 来操作软件
- 把“调用任意本机软件工具”从一次性命令，提升为 **可复用、可审计、可扩展** 的技能化工作流
- 为后续单个软件的专用桥接能力提供统一模板

## 触发场景

- 用户要求“调用本机任意软件”“把某个桌面软件接给 AI”
- 用户希望在 Trae 中统一操作已安装的本地工具、命令行程序、桌面软件
- 用户要求为某个具体软件建立可复用的桥接调用方式
- 用户要求参考 `CLI-Anything` 创建类似能力，但当前平台不是其原生插件运行环境

## 设计目标

本技能追求的是 **等效能力**，不是 **源码级照搬**。

在当前工作区中，等效能力定义为：

1. **可发现**：先识别本机是否存在目标软件、可执行文件、版本与帮助命令。
2. **可调用**：优先走软件自带 CLI、无头参数、脚本接口或本地桥接层。
3. **可验证**：每次调用前后都要有探测命令、输出校验与错误信息。
4. **可扩展**：新软件可按统一模板快速补充成“单工具子规范”。
5. **可控风险**：高危操作仍受 Trae 命令审批和用户授权约束。

## 能力分层

调用本机软件时，必须按以下优先级选择路径：

1. **原生命令行 / 官方 CLI**
   - 例如 `<tool> --help`、`<tool> --version`
   - 最优先，因为最稳定、最可组合、最适合代理

2. **无头模式 / 脚本接口**
   - 例如 `--headless`、批处理、脚本文件入口、嵌入式解释器
   - 适用于桌面软件但具有自动化后门的场景

3. **本地 API / 本地服务 / MCP 桥接**
   - 适用于软件暴露了本机服务端口、REST、WebSocket、JS Bridge、插件桥接的情况

4. **受控 GUI 自动化**
   - 仅在前三层都不可用时作为最后手段
   - 必须先说明脆弱性，不得伪装成稳定集成

## 标准流程

### 1. 探测

- 先确认目标软件名称、路径、调用方式
- 优先探测：
  - `Get-Command <tool>`
  - `where.exe <tool>`
  - `<tool> --version`
  - `<tool> --help`

### 2. 建模

- 判断该软件属于哪一类：
  - CLI 工具
  - GUI 但支持无头
  - GUI 但支持脚本
  - GUI 但支持本地桥接
  - 纯 GUI 无稳定接口

### 3. 最小可用调用

- 先做只读探测命令
- 再做最小副作用调用
- 最后才做真实业务动作

### 4. 校验

- 校验退出码
- 校验标准输出 / 错误输出
- 校验目标产物是否存在
- 必要时回读结果文件或再次探测状态

### 5. 固化

- 若该软件后续还会频繁使用，按模板补一份单工具适配说明
- 推荐放在：
  - `doc/技能库/cli-anything-local-tools/TOOL-HARNESS-TEMPLATE.md`
  - 或后续新增 `doc/技能库/<具体工具名>/...`

## 安全边界

本技能允许“调用任意本机软件”的前提，是仍然遵守 Trae 原生的命令与审批门禁。

### 默认允许

- 只读探测：版本、帮助、状态、列目录、读取本地输出
- 项目内可逆操作：生成中间文件、导出结果、运行无头分析

### 必须谨慎

- 修改用户主目录外的重要文件
- 改写系统配置、环境变量、注册表
- 安装 / 卸载软件
- 长驻后台服务
- 涉及账户、网络、凭据、数据库、金融或生产环境的本机工具

### 必须显式告知风险

- 需要管理员权限
- 需要外部网络
- 会改变系统级状态
- 会直接驱动真实桌面应用执行写操作

## 平台适配说明

当前环境为 Windows + PowerShell，优先采用以下形式：

```powershell
Get-Command tool-name
where.exe tool-name
tool-name --version
tool-name --help
```

如软件仅支持绝对路径启动，应优先使用绝对路径，并在命令前说明目标路径来源。

## 与 CLI-Anything 的关系

本技能借鉴的是它的核心思想：

- 以代理友好的接口代替脆弱的人类 GUI 操作
- 优先使用真实软件后端，而不是伪造能力
- 将发现、调用、验证、文档化做成一套标准流程

但本技能 **不承诺** 在当前仓库中完整复制其“7 阶段自动生成 CLI + 测试 + 发布”的全套体系。当前落地的是：

- 一个可被 Trae 自动发现的桥接技能
- 一套本机工具调用 SOP
- 一个可扩展的单工具适配模板

## 本项目安装结论

当前工作区已采用本地桥接方式安装该技能：

- 主源文档：`doc/技能库/cli-anything-local-tools/SKILL.md`
- 本地入口：`.trae/skills/cli-anything-local-tools/SKILL.md`
- 适配模板：`doc/技能库/cli-anything-local-tools/TOOL-HARNESS-TEMPLATE.md`

## 主源位置

- `doc/技能库/cli-anything-local-tools/SKILL.md`
- `doc/技能库/cli-anything-local-tools/TOOL-HARNESS-TEMPLATE.md`
- `.trae/skills/cli-anything-local-tools/SKILL.md`
- `doc/操作记录/20260705-参照CLI-Anything创建本机工具桥接技能记录.md`
