# using-superpowers 并入 Edict 调整记录

## 1. 调整目标
- 将 `using-superpowers` 的总控能力吸收到本地 `Trae Edict（三省六部）` 工作流。
- 降低默认会话中的重复流程提示与技能引入成本。

## 2. 本次修改范围
- `.trae/rules/edict-workflow.md`
- `.trae/skills/edict-triage/SKILL.md`
- `.trae/skills/edict-planning/SKILL.md`
- `.trae/skills/edict-review/SKILL.md`
- `.trae/skills/edict-dispatch/SKILL.md`
- `.trae/skills/using-superpowers/SKILL.md`
- `doc/设计文档/20260512-using-superpowers并入Edict设计说明.md`

## 3. 实际并入内容
- “先判技能、后行动”的起手门禁
- “流程技能优先于实现技能”的调度优先级
- “checklist 必须转 Todo”的落地规则
- “执行模式需要显式选择”的计划要求
- “遗漏明显适用流程技能必须驳回”的审议规则

## 4. 调整结果
- `Edict` 已成为本地默认总控工作流，覆盖 superpowers 中最关键的总控职责。
- `using-superpowers` 已压缩为桥接层，不再重复展开整套总控正文。
- `Edict` 四个技能入口已同步补充技能路由、checklist 落地与流程监督能力。

## 5. 未做的事情
- 未删除 `using-superpowers`
- 未移除各个专业流程技能
- 未把所有 superpowers 技能正文并入 `Edict`

## 6. 原因
- 完全删除桥接层会降低外部概念兼容性。
- 把所有流程技能正文并入 `Edict` 会导致三省六部重新膨胀，反而增加 token 消耗。
- 当前方案是“总控能力内聚，专业技能保留独立”。
