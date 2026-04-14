# 审计报告目录迁移：解决思路与执行步骤

## 1. 任务背景与目标
用户（美女程序员 xy）要求将原先存放在 `codeworkfiles/audit_reports` 的审计报告目录迁移到 `doc/` 目录下（即 `doc/audit_reports`）。此操作旨在将项目规范、文档、审计报告进行统一的结构化管理。

## 2. 解决思路
- **物理迁移**：使用终端命令将整个 `audit_reports` 文件夹及其中已存在的文件（如职责规范文档）移动到 `doc` 目录下。
- **配置与文档更新**：因为目录发生了变更，所以必须同步修改之前在 `PM_Audit_Responsibilities.md` 中写死的路径引用，防止开发者找不到最新的审计意见。
- **记忆更新**：更新核心记忆中对应的路径规则，确保未来系统在生成审计报告时直接指向 `doc/audit_reports`，而不是老的目录。

## 3. 执行步骤
1. **移动目录**：执行命令 `mkdir -p doc && mv codeworkfiles/audit_reports doc/`，完成物理目录迁移。
2. **修正文档引用**：读取 `doc/audit_reports/PM_Audit_Responsibilities.md` 文件，将所有的 `codeworkfiles/audit_reports` 替换为 `doc/audit_reports`。
3. **更新核心记忆**：使用 `manage_core_memory` 工具，将 `03fv5f6npkaae2jdwiol39xhu` 的规则记录中相关的路径更新为 `doc/audit_reports`。
4. **生成本步骤记录**：遵循“总是将步骤和解决思路生成 markdown 文件放在 codeworkfiles 目录下”的原则，编写并保存本文件。