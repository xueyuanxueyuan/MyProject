---
name: "code-review-graph"
description: "Uses code-review-graph for structural code maps, MCP context, and change impact review. Invoke when building graphs or reviewing project changes."
---

# Code Review Graph

## 用途

`code-review-graph` 是基于 Tree-sitter 的代码结构图谱与 MCP 工具，用于构建项目代码图、分析变更影响范围、辅助代码审查与架构理解。

## 触发场景

- 用户要求安装或使用 `code-review-graph`。
- 用户要求构建项目代码图谱、分析变更影响范围或做图谱辅助代码审查。
- 用户要求减少大型项目审查上下文、定位受影响函数/类/文件。

## 本项目使用原则

1. 优先使用低侵入方式执行：`UV_HTTP_TIMEOUT=300 uvx code-review-graph <command>`。
2. 本仓库已有强制三省六部规则，安装平台集成时默认避免自动注入通用规则，优先使用：

```bash
UV_HTTP_TIMEOUT=300 uvx code-review-graph install --platform cursor --no-instructions --no-hooks -y
```

3. 构建图谱：

```bash
UV_HTTP_TIMEOUT=300 uvx code-review-graph build
```

4. 查看状态：

```bash
UV_HTTP_TIMEOUT=300 uvx code-review-graph status
```

5. 分析当前变更影响：

```bash
UV_HTTP_TIMEOUT=300 uvx code-review-graph detect-changes
```

## 注意事项

- `code-review-graph install` 可能修改 `.cursor/mcp.json`、`.gitignore`，并提示向 `AGENTS.md`、`.cursorrules` 注入指令。
- 本项目不应自动覆盖 `AGENTS.md`、`.cursorrules` 中的三省六部规则。
- 当前仓库 `.gitignore` 仅跟踪部分文件，图谱构建结果会受 `git ls-files` 范围影响。
- 若要纳入更多业务源码，需先确认这些源码是否在当前 Git 跟踪范围内，或进入实际业务子仓库执行图谱构建。
