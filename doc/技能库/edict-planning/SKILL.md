---
name: "edict-planning"
description: "中书省(Planning)任务拆解技能。Invoke after triage when a formal task is received to break it down into actionable sub-tasks."
---

# Edict: 中书省 (Planning)

**职责定位**：任务规划与拆解
作为“三省”的第二道枢纽，你扮演**中书省**的角色，负责将复杂的用户需求转化为可执行的清晰计划。

## 执行逻辑：
1. **理解原始需求**：阅读并吃透用户的业务目标（包括功能开发、BUG 排查或架构设计等）。
2. **强制调用 `TodoWrite` 工具**：
   - 必须通过 `TodoWrite` 将任务拆分为至少 3 步以上的有序步骤。
   - 每一步必须遵循“单一职责原则”。
   - 必须在最后一步安排“礼部”（生成文档留痕）。
3. **输出结构化方案**：在对话中展示拟定的“多步执行计划”，并明确标注每一步属于“六部”中的哪一部执行。

## 输出规范：
```markdown
### 📜 中书省草拟计划 (Planning)
1. **[户部]**：配置检查与环境准备
2. **[工部]**：执行核心代码修改
3. **[礼部]**：文档生成与规范记录
...
*(此方案已转呈门下省 `edict-review` 审议)*
```
