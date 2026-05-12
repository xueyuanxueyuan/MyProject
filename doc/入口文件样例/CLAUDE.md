# 统一主规则

本文件是当前仓库的统一长正文主源，负责声明默认工作流、优先级、项目通用规范和交付门禁。为兼容 Trae 本地规则发现，以下文件保留为薄入口或专项补充：
- `.trae/rules/edict-workflow.md`
- `.trae/rules/anti-laziness-anti-hallucination.md`
- `.trae/rules/workspace-common.md`

相关技能与说明主源：
- `doc/技能库/anti-laziness-anti-hallucination/SKILL.md`
- `doc/技能库/verification-before-completion/SKILL.md`
- `doc/设计文档/20260512-AI-Agent-防偷懒防撒谎能力统一说明书.md`

用户级风格偏好仍以 `~/.claude/CLAUDE.md` 为准。

## 1. 默认工作流
- 实质性任务默认先进入 `Trae Edict（三省六部）` 工作流，再并行启用 `anti-laziness-anti-hallucination`；临近交付默认联动 `verification-before-completion`。
- `Edict` 已吸收 `using-superpowers` 的总控能力：先判技能后行动、先流程技能再实现技能再基础工具、checklist 必须转为 `TodoWrite`。
- 实质性任务包括：开发、缺陷修复、重构、规则或脚本编写、影响面分析、代码评审、文档落地、多步骤分析，以及任何准备宣告“已完成”“已修复”“已通过”“已验证”的回复。

## 2. 三省六部主线
- **太子**：识别闲聊或正式任务，并先给出流程技能路由建议；需要澄清问题时，也必须先完成技能判定。
- **中书省**：先用 `TodoWrite` 拆 3 步以上可验证子任务；把命中的流程技能、checklist、阶段门禁和执行模式写进计划。
- **门下省**：审查规则、记忆、技能完备性和执行模式；高风险操作必须通过 `AskUserQuestion` 请求用户批准。
- **尚书省**：先调度流程技能，再调度实现技能和基础工具；仅在任务相互独立时并行派发。
- **六部**：吏部管技能与记忆，户部管资源与工具链，礼部管文档留痕，兵部管编译/发布，刑部管质控与自动化验收，工部管编码实现。
- 调用 `edict-triage`、`edict-planning`、`edict-review` 等三省流程技能后，必须在紧随其后的对用户可见回复中，显式按该技能 `输出规范` 回显对应 Markdown 规范块；仅调用技能工具而未回显，不算完成该环节。
- 若已调用流程技能却仍使用自然语言进度更新替代规范块，视为未按三省六部规范执行；可在规范块之后追加简短说明，但不得省略规范块本身。

## 3. 流程路由
- 新需求、行为调整、方案讨论：优先 `brainstorming`
- Bug、测试失败、异常行为：优先 `systematic-debugging`
- 新功能或缺陷修复进入编码：优先 `test-driven-development`
- 需要隔离环境或新分支执行：优先 `using-git-worktrees`
- 已有成文计划：优先 `executing-plans`
- 当前会话内按计划拆块开发：优先 `subagent-driven-development`
- 2 个以上独立子任务：优先 `dispatching-parallel-agents`
- 关键阶段完成或合并前：优先 `requesting-code-review`
- 收到评审意见：优先 `receiving-code-review`
- 准备宣告完成：优先 `verification-before-completion`

## 4. 项目通用规范
- 代码必须符合对应语言与框架规范，包括 Java、Vue、TypeScript、JavaScript。
- 所有任务必须优先阅读 `doc` 目录下相关文档。
- `doc/项目规范` 是项目规范主源；`doc/提示词` 是任务模板与输出模板；`doc/技能库` 是技能主源。
- 若 `doc/技能库` 与 `.cursor/skills`、`.trae/skills` 存在同名技能，必须以 `doc/技能库` 为归档和理解主源。
- 涉及编译、打包、发布或工具链切换时，必须同时遵守 `doc/项目规范` 的工具链规范和 `doc/技能库` 的对应技能说明。

## 5. 交付门禁
- 先查证：先读相关文档、规则、技能、目标文件和现状实现，禁止凭记忆下结论。
- 先拆解：先明确验证方式，再实施；技能中的 checklist 和检查点必须落入 `TodoWrite`。
- 禁占位：禁止 `TODO`、`...`、空实现、伪代码占位和把应完成工作转嫁给用户。
- 无验证不报喜：任何成功表述前必须完成最新验证并核对结果；测试、构建、修复、子 Agent 成功均不得口头认定。
- 真实兜底：无法确认时只能明确写“未验证”“仅完成局部验证”“无此数据”等真实状态。
- 任何“已完成”表述前，至少执行一次 `scripts/guardrails/agent-delivery-guardrail.sh --scan-only`。
- 需要声称测试、构建或检查通过时，使用 `--verify-command "<command>"` 或 `--auto-verify` 获取最新证据。

## 6. 子 Agent 复核
- 子 Agent 的“成功”只是线索，不是事实。
- 必须复核实际改动、diff、命令输出后，才能对外汇报。

## 7. 优先级
- 用户明确指令最高，其次是用户级 `~/.claude/CLAUDE.md`。
- 项目记忆与本文件共同构成当前仓库主约束；如与用户级规则冲突，以用户级为准；如与项目记忆冲突，以项目记忆为准。
- `.trae/rules/*.md` 仅作为本文件的薄入口或专项补充，不与本文件争夺主源地位。

## 8. Final Rule
- 没有最新证据，不输出成功结论。
- 交付物不完整，不伪装成已完成。
