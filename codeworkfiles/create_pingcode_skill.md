# 创建 PingCode UI 自动化筛选技能

## 需求背景
用户提出将我们刚刚成功实现的“PingCode UI 自动化精准两行条件筛选”的能力封装为一个可以复用的**技能 (Skill)**，以便未来随时直接调用。

## 解决思路
根据全局规范，技能文件的创建和管理必须遵循以下规则：
1. **源文件目录**：技能源文件保存在 `/doc/skills` 下。
2. **智能体专属目录**：技能必须双向同步至使用的智能体工具（当前为 Trae），因此需写入 `.trae/skills`。
3. **格式规范**：符合 `SKILL.md` 的 Frontmatter 要求（包含 `name` 和限制在 200 字以内的 `description`），并在正文中包含技能的详细交互逻辑与代码。

## 执行步骤
1. **调用 Skill Creator 工具**：
   - 首先通过工具调用了 `skill-creator` 获取标准的技能生成规范，确认目录和字段要求。
2. **生成与配置技能文件**：
   - 创建了技能名称为 `pingcode-ui-filter`。
   - 配置 Description 明确说明：“当用户需要通过前端UI自动化（CDP）在PingCode页面精确筛选特定任务数据（如状态、负责人等）时调用此技能”。
   - 将我们在排查过程中摸索出的核心经验法则总结入内，特别是“强制点击重置清空历史条件”、“查找 .cdk-overlay-container”、“时序等待 sleep”等关键知识。
3. **写入双目录**：
   - 写入源目录：`doc/skills/pingcode-ui-filter/SKILL.md`
   - 写入 Trae 工作区：`.trae/skills/pingcode-ui-filter/SKILL.md`
4. **更新核心记忆**：
   - 之前已经将 PingCode DOM 结构的知识纳入 `<project_memories>`，现在固化为技能后，该能力成为长期稳定的工作流资产。

## 成果
技能现已成功装载至本地智能体配置中，今后只需提及“自动化筛选 PingCode 数据”，即可直接利用此技能进行最精准的列表过滤。