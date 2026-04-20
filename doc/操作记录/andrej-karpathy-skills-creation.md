# Andrej Karpathy Skills 生成记录

## 解决思路
1. **理解需求**：将 `https://github.com/forrestchang/andrej-karpathy-skills.git` 仓库中的内容拽下来，并生成一个适用于 Trae 的 Skill。
2. **规范约束**：
   - 根据全局配置记忆，所有的 Skill 必须存放在 `doc/技能库` 作为源文件目录，并同步至 `.trae/skills` 目录。
   - 步骤和解决思路需要记录到 `codeworkfiles` 目录下的 Markdown 文件中。
3. **执行步骤**：
   - 第一步：使用 `git clone` 将目标仓库克隆到临时目录 `/tmp/andrej-karpathy-skills` 中。
   - 第二步：读取该仓库中的核心文件 `CLAUDE.md`，了解其内容（主要包含 Andrej Karpathy 的 4 个 LLM 编码原则：Think Before Coding, Simplicity First, Surgical Changes, Goal-Driven Execution）。
   - 第三步：将其转化为标准的 Trae Skill 格式（添加 Frontmatter：`name` 和 `description`）。
   - 第四步：创建目录 `doc/技能库/andrej-karpathy-skills` 和 `.trae/skills/andrej-karpathy-skills`。
   - 第五步：将转化后的内容分别写入两个目录的 `SKILL.md` 文件中，实现双向同步。
   - 第六步：清理临时下载的文件（可选，但为了环境整洁可以清理）并生成此思路文档。

## 结果
- 主技能目录文件：`doc/技能库/andrej-karpathy-skills/SKILL.md`
- Trae 技能文件：`.trae/skills/andrej-karpathy-skills/SKILL.md`
- 技能创建完毕，现已能在 Trae 中直接通过该技能提升代码生成的稳定性和规范性。