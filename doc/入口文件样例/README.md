# 入口文件样例

本目录用于保存当前工作区已验证可用的入口文件样例，便于跨项目、跨工具迁移时直接参考或派生。

## 当前样例
- `CLAUDE.md`
- `cursorrules-统一主规则桥接片段.md`（供 `.cursorrules` 顶部 §0 复制）
- `.trae/rules/edict-workflow.md`
- `.trae/rules/anti-laziness-anti-hallucination.md`
- `.trae/rules/workspace-common.md`

Cursor 工作区另维护（**须 `alwaysApply: true` 的 `.mdc`**）：
- `.cursor/rules/edict-workflow.mdc`
- `.cursor/rules/anti-laziness-anti-hallucination.mdc`
- 根目录 `AGENTS.md`（Agent 入口检查清单）

## 使用方式
- 将 `doc/项目规范/统一主规则-通用工具链接入说明.md` 作为唯一标准源。
- 若目标工具支持自动或半自动生成入口文件，应优先参考本目录中的样例结构与字段。
- 若目标工具还支持技能入口或桥接技能文档，应再配合 `doc/通用模板/技能入口统一模板.md` 生成对应入口。
- 生成后的入口文件需保留当前工作区默认调用方式，不得遗漏 `Edict`、交付门禁、流程技能路由、主源关系、低风险同回合继续执行与结果复核要求。

## 同步约定
- 当仓库根 `AGENTS.md`、`CLAUDE.md` 或 `.trae/rules/edict-workflow.md` 发生规则收敛时，应同步更新本目录样例，避免模板漂移。
- `CLAUDE.md` 样例应优先保持“主规则索引 + 不可删门禁”的结构，不重复展开技能细节。
- `edict-workflow.md` 样例应显式包含门下省低风险不得中断、尚书省调度后由翰林院汇总回奏等关键约束。
