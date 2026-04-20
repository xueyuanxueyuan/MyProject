# Edict 平台（三省六部制）基础架构实现方案

## 1. 架构目标
根据用户需求，在 Trae 中参照 `cft0808/edict` 思想，搭建一套具备实际运行能力的“三省六部制”调度平台，通过独立的 Python 调度脚本及对应的 Trae 技能库（Skills），将大模型的处理流程切分为具体的智能体阶段。

## 2. 平台构成与落地方案

### 2.1 调度中心：`edict-platform/` 目录
创建了 `edict-platform` 作为独立的运行框架：
- **`core/orchestrator.py`**：这是系统的心脏，定义了 `Emperor` (皇上)、`Triage` (太子)、`Planning` (中书省)、`Review` (门下省)、`Dispatch` (尚书省) 和 `Ministry` (六部) 之间的调用链逻辑。
- 此脚本可通过 Python 直接运行模拟调度流程，为后续的 Agentic 工作流提供骨架。

### 2.2 Trae 核心技能：`edict-*` 系列
不仅在外部实现了脚本，我们将 Trae 本身的智能也划分为 4 个核心枢纽技能（保存在 `doc/技能库/` 并双向同步至 `.trae/skills/`）：
1. **`edict-triage` (太子)**：用于前置意图拦截，快速区分闲聊和工作任务。
2. **`edict-planning` (中书省)**：用于强制调用 `TodoWrite`，把复杂需求转化为单一职责的步骤，并分配到六部。
3. **`edict-review` (门下省)**：用于校验中书省的计划是否符合 `.trae/rules`，拦截高风险操作。
4. **`edict-dispatch` (尚书省)**：用于根据审核通过的计划，并发调度对应的技能或工具给六部执行。

## 3. 六部分工映射
在尚书省派发后，调用相关工具或 Skill 必须符合各部定位：
- **吏部**：使用 `skill-creator` 或 `manage_core_memory`。
- **户部**：专注安全配置（如 `python-db-encrypted-auth`），管理环境变量与工具链 (`vfox-toolchain`)。
- **礼部**：使用 `Write` 生成文档留痕至 `doc` 目录。
- **兵部**：调用部署工具如 `gjj-build-deploy`、`gjj-remote-deploy`。
- **刑部**：调用代码规范检查 `GetDiagnostics` 及 `browser-use` 等进行测试筛选。
- **工部**：负责使用 `Read` / `Write` / `SearchReplace` 进行核心代码的构建。

## 4. 落地效果
当前，当用户（皇上）下达任何大型指令时，平台（无论通过 Python 脚本还是通过 Trae 本身触发 `edict-triage` 技能），均能展现出与 `cft0808/edict` 相同级别的“拆解、审查、分发”的智能体流水线协同效果。避免了大模型一头扎进复杂需求导致的失控问题。