# AI Agent 防偷懒防撒谎全局模板

## 目标

把“不要偷懒、不要撒谎”从一句口号，落成可复制的三层闭环：

- 技能层：告诉智能体何时必须进入查证与验证模式
- 规则层：让工作区默认启用这些门禁
- 守卫层：在本地脚本里做占位符扫描与真实验证

## 推荐复制到其他项目的文件

- `.trae/skills/anti-laziness-anti-hallucination/SKILL.md`
- `.trae/rules/anti-laziness-anti-hallucination.md`
- `scripts/guardrails/agent-delivery-guardrail.sh`

如果目标项目也维护技能主源，建议同步保留：

- `doc/技能库/anti-laziness-anti-hallucination/SKILL.md`

## 推荐规则模板

可直接写入目标项目的工作区规则：

```markdown
# AI Agent 防偷懒与防撒谎规则

- 实质性任务开始前，必须先查证相关文档、文件、接口或目录，禁止只凭记忆输出。
- 进入开发、排障、规则编写、脚本编写等任务后，必须先拆分步骤并确定验证方式。
- 禁止交付 `TODO`、`...`、`其余保持不变`、空实现、伪代码式占位方案。
- 任何“已完成”“已修复”“已通过”类表述前，必须先执行最新验证并核对结果。
- 子智能体、脚本或工具的成功回报不能直接采信，必须复核结果。
- 无法验证时必须明确说明“未验证/无此数据/仅完成局部验证”，禁止模糊承诺。
```

## 推荐全局提示词补丁

如果工具支持用户级或全局 system prompt，可补充以下约束：

```markdown
## Agent Delivery Integrity
- Do not trust memory for repo facts, dependency names, or build status. Verify before claiming.
- Do not leave placeholders, skeletal code, or "rest unchanged" style delivery.
- Before any success claim, run the proving command and read its real output.
- If verification is missing, say so explicitly instead of guessing.
```

## 推荐脚本用法

```bash
# 扫描当前改动
scripts/guardrails/agent-delivery-guardrail.sh --scan-only

# 扫描并执行指定验证
scripts/guardrails/agent-delivery-guardrail.sh --verify-command "npm run build"
```

## 落地建议

1. 先复制技能和规则
2. 再把守卫脚本放进目标仓库
3. 最后在团队内统一“无验证不宣告完成”的口径

这样才能把“约束智能体”从提示词层，推进到真正可执行的工程闭环。
