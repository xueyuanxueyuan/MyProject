# using-superpowers 并入 Edict 设计说明

## 1. 目标
- 将 `using-superpowers` 中高价值、低耦合的“总控流程能力”并入 `Trae Edict（三省六部）`。
- 减少默认工作流层面的重复技能引入，降低 token 消耗。
- 保留 `using-superpowers` 作为术语桥接层，避免完全失去外部生态兼容性。

## 2. 并入原则
- 不原样搬运全部 `superpowers` 技能正文。
- 只吸收“总控层能力”，不把具体实现技能本身消解掉。
- 保持 `Edict` 继续作为本地默认工作流主源。

## 3. 本次吸收的能力
### 3.1 会话起手总控
- 每次收到新请求后，先做技能判定，再做任何回复、澄清、搜索、执行或修改。
- 这部分并入 `Edict` 的 `0.1 总控门禁` 与“太子”职责。

### 3.2 流程优先级
- 先流程技能，再实现技能，再基础工具。
- 典型优先顺序：`brainstorming / systematic-debugging / writing-plans / test-driven-development` 优先于领域技能与普通工具。
- 这部分并入 `Edict` 的总控门禁和“尚书省”调度规则。

### 3.3 checklist 落地
- 技能中出现的 checklist、阶段门禁、检查点，必须转为 `TodoWrite` 可验证步骤。
- 这部分并入“中书省”与“尚书省”。

### 3.4 执行模式选择
- 根据计划成熟度和任务独立性，在 `executing-plans`、`subagent-driven-development`、`dispatching-parallel-agents`、`using-git-worktrees` 之间做明确路由。
- 这部分并入“中书省”与“门下省”。

### 3.5 流程完备性审查
- 若计划明显遗漏适用流程技能或验证闭环，必须驳回重拟。
- 这部分并入“门下省”。

## 4. 明确未并入的内容
- `test-driven-development`
- `systematic-debugging`
- `requesting-code-review`
- `receiving-code-review`
- `using-git-worktrees`
- `subagent-driven-development`
- `dispatching-parallel-agents`
- `executing-plans`

原因：
- 这些技能属于“专业流程技能”，适合继续独立存在，由 `Edict` 负责默认路由与调度，而不是并入正文后变成大杂烩。

## 5. 最终架构
- `Edict`：本地默认总控工作流，负责分拣、路由、拆解、审议、调度、监督。
- `using-superpowers`：本地桥接层，负责说明 superpowers 总控理念已由 `Edict` 吸收。
- 其他流程技能：保留为独立能力模块，由 `Edict` 负责命中和调度。

## 6. 收益
- 减少一层重复的“先判技能再行动”长提示词。
- 保持三省六部在本地语境下更完整、更强的总控能力。
- 继续兼容外部 superpowers 概念，降低迁移和理解成本。
