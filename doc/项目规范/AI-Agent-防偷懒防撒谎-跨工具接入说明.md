# AI Agent 防偷懒防撒谎能力跨工具接入说明

## 1. 目标

把 `doc/设计文档/20260512-AI-Agent-防偷懒防撒谎能力统一说明书.md` 翻译为本仓库可直接接入的工具链能力，形成：

1. 统一能力源（文档+技能）
2. 统一执行入口（守卫脚本）
3. 工具侧规则模板（Codex/Claude/Cursor）
4. 最低自检流程（交付前必走）

## 2. 能力源

- 统一说明书：`doc/设计文档/20260512-AI-Agent-防偷懒防撒谎能力统一说明书.md`
- 技能主源：`doc/技能库/anti-laziness-anti-hallucination/SKILL.md`
- Trae 规则入口：`.trae/rules/anti-laziness-anti-hallucination.md`

## 3. 五道硬门禁（必须保留）

1. Context Gate：先查证，再动手
2. Planning Gate：先拆解，再实施
3. Delivery Gate：禁止占位交付
4. Verification Gate：无验证不报喜
5. Honesty Gate：无法确认就说真实状态

## 4. 统一执行入口

- 守卫脚本：`scripts/guardrails/agent-delivery-guardrail.sh`

常用命令：

```bash
# 仅扫描偷懒占位痕迹
scripts/guardrails/agent-delivery-guardrail.sh --scan-only

# 扫描 + 指定真实验证
scripts/guardrails/agent-delivery-guardrail.sh --verify-command "<你的验证命令>"

# 扫描 + 自动识别验证命令
scripts/guardrails/agent-delivery-guardrail.sh --auto-verify
```

## 5. 各工具接入模板

### 5.1 Codex

若工具目录可写，建议创建：`.codex/rules/anti-laziness-anti-hallucination.md`

最小模板：

```md
命中实质性任务默认启用防偷懒防撒谎能力。
交付前必须通过：先查证、先拆解、禁占位、做验证、诚实兜底。
无验证不得宣告完成。
本地守卫：scripts/guardrails/agent-delivery-guardrail.sh --scan-only
```

### 5.2 Claude

建议创建：`.claude/rules/anti-laziness-anti-hallucination.md`

最小模板与 Codex 同义，重点保留“五道硬门禁 + 无验证不宣告完成”。

### 5.3 Cursor

建议创建：`.cursor/rules/anti-laziness-anti-hallucination.md`

最小模板与 Codex 同义，重点保留“默认触发 + 验证优先 + 真实兜底”。

## 6. 交付前最低自检

1. 已读取相关上下文（文档/规则/代码）
2. 已拆分可验证步骤
3. 交付物无 `TODO` / `...` / 空实现 / 转嫁话术
4. 已执行最小充分验证
5. 无证据部分明确标注 `未验证` 或 `仅局部验证`

## 7. 当前仓库状态说明（2026-05-12）

- Trae 侧已接入规则与技能。
- Guardrail 脚本已可用。
- 若 `.codex` 为只读目录，可先使用本说明中的模板，由对应工具管理员在可写环境落盘。
