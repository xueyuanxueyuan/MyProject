# Trae 仿“三省六部”(Edict) 架构设计与搭建方案

## 1. 调研与架构理念
根据对 `cft0808/edict` 项目的调研，该项目是一个基于古代中国“三省六部制”的 AI Multi-Agent Orchestration 系统。它通过分权制衡（Separation of Powers）来避免单体大模型在复杂任务中的幻觉、逻辑崩塌和权限失控问题。
- **核心流程**：皇上(用户) ➔ 太子(分拣) ➔ 中书省(规划) ➔ 门下省(审议) ➔ 尚书省(派发) ➔ 六部(专业执行) ➔ 回奏(总结)。

为了在 **Trae IDE** 中为你（xy 美女架构师）搭建同类架构，我们将 Trae 内置的工具链（Tools）、记忆机制（Core Memory）和工作流（Todo/Rules）映射到这十二个角色上，形成一套严密的自动化工作流规范。

## 2. 角色映射与 Trae 能力绑定

| Edict 角色 | 职责定位 | Trae 对应的工具链与能力映射 |
| :--- | :--- | :--- |
| **皇上 (User)** | 提出需求与最终决策 | 开发者（xy），指令下发者，高风险确认者 |
| **太子 (Triage)** | 意图识别与分拣 | Trae 意图识别：闲聊（提供情绪价值） vs 任务指令 |
| **中书省 (规划)** | 任务拆解与多步计划 | `TodoWrite` 工具：为复杂任务生成结构化任务列表 |
| **门下省 (审议)** | 合规性审查与驳回 | 读取 `.trae/rules` 和全局记忆，校验计划合规性；高风险调用 `AskUserQuestion` |
| **尚书省 (派发)** | 并行工具调用调度 | Trae 的 `Parallel Tool Calls` 能力，批量执行任务 |
| **吏部 (管理)** | 记忆与技能管理 | `manage_core_memory` 提取经验规则，`skill-creator` 创建新技能 |
| **户部 (资源)** | 数据库与依赖配置 | `python-db-encrypted-auth` 处理安全配置，`vfox-toolchain` 准备环境 |
| **礼部 (规范)** | 文档与 UI/UX 设计 | 生成 `codeworkfiles` 记录，保证 Java/Vue/TS 编码规范及前端体验 |
| **兵部 (部署)** | 编译、打包与发布 | `gjj-build-deploy` 与 `gjj-remote-deploy` 进行自动化集成和远程发布 |
| **刑部 (质控)** | 审查、排错与测试 | `GetDiagnostics` 检查 Lint 错误，`browser-use` 和 `settlement-window-verifier` 验证 UI |
| **工部 (编码)** | 核心代码构建与重构 | `Read` / `Write` / `SearchReplace` 进行精准的代码外科手术式修改 |

## 3. 解决思路与落地步骤
1. **意图规范化**：通过建立一个全局的 Trae 规则文件（`.trae/rules/edict-workflow.md`），强制 AI 在执行复杂任务时采用“分拣 ➔ 规划 ➔ 审议 ➔ 派发执行 ➔ 回奏”的思维链（Chain of Thought）。
2. **记录留痕化**：规定所有的架构调整和问题排查，必须由“礼部”自动在 `codeworkfiles` 下生成 Markdown 记录。
3. **权限卡控化**：在“门下省”环节增加校验逻辑，如果涉及核心代码重构或直接部署，必须向用户进行确认（高风险卡控）。
4. **专业分工化**：AI 根据任务特征，精准调用“六部”对应的专属 Skill（如使用 `gjj-remote-deploy` 绝不使用普通 shell）。

## 4. 架构成果
通过本方案的实施，Trae 已经不仅是一个简单的补全工具，而是进化成了一个具备完整自我规划、自我审查、分工明确的**“微型 AI 帝国”**。此架构能够完美承载复杂的 Gjj 系统开发和发布任务，大幅降低出错率！