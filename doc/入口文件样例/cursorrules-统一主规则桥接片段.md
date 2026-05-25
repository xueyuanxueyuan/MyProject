# cursorrules 统一主规则桥接片段（样例）

复制到目标仓库 **`.cursorrules` 最顶部**（§0），勿粘贴 `CLAUDE.md` 全文。项目专属内容接在其后另起章节。

```md
## 0. 统一主规则桥接（Cursor 统一入口）

**分工**：工作流、交付门禁、优先级以仓库根 **`CLAUDE.md`** 为准；**本文件**负责本项目编码约束与专属约定。勿在多处重复粘贴 `CLAUDE.md` 全文。

**实质性任务必须**：

1. 使用 **`.cursor/rules/edict-workflow.mdc`**（`alwaysApply: true`）：每条实质性回复必须先输出太子/中书/门下三块；低风险时同回合继续执行，且 **`TodoWrite` ≥3 步**。
2. 使用 **`.cursor/rules/anti-laziness-anti-hallucination.mdc`**（`alwaysApply: true`）：先查证；禁占位；无验证不报喜。
3. 宣告完成前：`scripts/guardrails/agent-delivery-guardrail.sh --scan-only`（构建/测试声称通过时用 `--verify-command`）。
4. 子 Agent 输出须复核 diff 与命令结果。

**主源索引**：`doc/项目规范/`、`doc/提示词/`、`doc/技能库/`（同步 `.cursor/skills/`）、`doc/项目规范/统一主规则-通用工具链接入说明.md`。
```

配套文件：从 `doc/入口文件样例/.trae/rules/edict-workflow.md` 派生 **`.cursor/rules/edict-workflow.md`**（Cursor 版见本仓库 `.cursor/rules/edict-workflow.md`）。
