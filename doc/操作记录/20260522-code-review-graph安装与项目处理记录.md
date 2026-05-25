# code-review-graph 安装与项目处理记录

## 1. 任务背景

用户要求安装 `https://github.com/tirth8205/code-review-graph.git` 对应能力，并对当前 Gjj 项目执行处理。

经核查，该仓库不是单纯的 Trae 技能包，而是 Python CLI + MCP 工具：通过 Tree-sitter 构建代码结构图谱，并提供状态检查、变更影响分析、MCP 上下文等能力。

## 2. 执行策略

本项目已有强制三省六部规则，且 `AGENTS.md`、`.cursorrules` 是工作流入口文件。为避免外部工具自动注入通用规则覆盖项目主规则，本次采用低侵入安装策略：

```bash
UV_HTTP_TIMEOUT=300 uvx code-review-graph install --platform cursor --no-instructions --no-hooks -y
```

同时补充本项目技能入口：

- `.trae/skills/code-review-graph/SKILL.md`
- `doc/技能库/code-review-graph/SKILL.md`

## 3. 安装过程

首次执行：

```bash
uvx code-review-graph --help
```

下载依赖 `jaraco-functools` 时出现网络超时。随后改用：

```bash
UV_HTTP_TIMEOUT=300 uvx --refresh code-review-graph --help
```

命令成功返回 CLI 帮助，确认工具可用。

## 4. 项目集成结果

执行 Cursor 平台 MCP 配置安装：

```bash
UV_HTTP_TIMEOUT=300 uvx code-review-graph install --platform cursor --no-instructions --no-hooks -y
```

结果：

- Cursor MCP 配置已存在或已配置到 `.cursor/mcp.json`。
- 跳过了向 `AGENTS.md`、`.cursorrules` 注入通用指令。
- 工具追加了 `.gitignore` 中的 `.code-review-graph/` 忽略项。

## 5. 图谱构建结果

执行：

```bash
UV_HTTP_TIMEOUT=300 uvx code-review-graph build
```

构建结果：

- 解析文件数：1
- 节点数：21
- 边数：94
- Flow 数：4
- Community 数：1
- 语言：Python

随后执行：

```bash
UV_HTTP_TIMEOUT=300 uvx code-review-graph status
```

状态结果：

- Nodes: 21
- Edges: 94
- Files: 1
- Languages: python
- Built on branch: main

## 6. 变更影响分析结果

执行：

```bash
UV_HTTP_TIMEOUT=300 uvx code-review-graph detect-changes
```

分析结果摘要：

- 分析到 34 个变更文件。
- 识别到 20 个变更函数或类。
- 影响 Flow 数为 0。
- 测试缺口数为 20。
- 整体风险分为 0.65。
- 主要集中在 `edict-platform/core/orchestrator.py`。

重点审查对象包括：

- `TaskSpec`
- `WorkflowContext`
- `Emperor.issue_edict`
- `Triage.process`
- `Triage.format_report`
- `HanlinAcademy.compose_rejection_report`
- `Planning.process`
- `Review.process`
- `Dispatch.process`
- `parse_args`

## 7. 注意事项

当前根仓库 `.gitignore` 说明该仓库主要跟踪 `doc/` 与少量入口文件，因此 `code-review-graph build` 实际只解析到 1 个 Python 文件。若后续希望对完整业务源码建立图谱，应进入实际业务子仓库，或确认业务源码已纳入 Git 跟踪范围后再执行构建。

本次未提交代码，未写入密钥，未覆盖三省六部主规则。
