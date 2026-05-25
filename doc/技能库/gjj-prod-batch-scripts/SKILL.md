---
name: gjj-prod-batch-scripts
description: 在 Gjj 仓库根目录通过 prod/IdeaProjects 布局批量改造一户通相关 Java 源码时，用于选用、排序与约束执行方式；执行前须读脚本与 git 状态。
---

# Gjj 工作区 prod 批处理脚本（工具链衔接）

本技能把仓库根目录下一组 **依赖 `./prod/IdeaProjects/capinfo-gjj-busi-jshs/` 相对路径** 的 Bash 脚本，纳入与本仓库 **Cursor 项目入口**（`.cursorrules` 中「工作区 prod 批处理脚本」段）及交付门禁一致的使用方式：先判定场景，再选脚本，再验证，禁止盲跑。

## 适用场景

- 需要在本地 **枣庄/一户通** 等业务改造中，对 `prod/` 下的后端工程做批量 `sed`/`awk`/整文件覆盖。
- 用户提到根目录 `modify_*.sh`、`fix_*.sh` 或要求「按脚本批量改 Java」。
- 评估是否应 **用手改 + 编译** 替代脚本（脚本多为一次性迁移，重复执行可能破坏已人工调整过的代码）。

## 前置条件（必须满足）

- **工作目录**：在仓库根目录 `/home/source/Jetbrains/Probject/Gjj`（或等价根路径）执行；脚本内路径均为 `./prod/...`。
- **目录存在**：`./prod/IdeaProjects/capinfo-gjj-busi-jshs/` 下目标模块与文件必须与脚本假定一致；若 `Not found` 输出为主，应先补齐布局或改写脚本路径，不可强行认定成功。
- **版本控制**：高破坏脚本运行前应有可回滚基准（分支或提交）；运行后立即 `git diff` 复核。
- **工具链**：修改 Java 后的验证遵循 `doc/项目规范` 与 `doc/技能库/vfox-toolchain`（JDK/Maven 版本）。

## 根目录脚本清单（按文件名）

以下均为仓库根目录、针对 **一户通/业务网关** 相关改造的历史批处理脚本，**语义以脚本正文为准**；本表仅作路由与风险提示。

| 脚本 | 作用摘要 | 风险等级 |
|------|----------|----------|
| `modify_entities.sh` | 向多处 DO/实体等追加 `dsjgbm`、`process_code`（`@TableField`） | 中：重复插入需靠 `grep` 防护 |
| `modify_dtos.sh` | 向多 DTO 追加 `dsjgbm`、`processCode`（`@Schema`） | 同上 |
| `modify_fkyw_skyw.sh` | `Fkyw`/`Skyw` 实体字段扩展 | 同上 |
| `modify_yhtywservice.sh` | 在 `YhtYwService` 接口中追加方法声明 | 中：`sed` 插入位置依赖源文件现状 |
| `fix_imports.sh` | 调整 `YhtYwService` 与其实现类的 import / 简短类型名 | 中 |
| `fix_yht_impl.sh` | **整文件重写** `YhtYwServiceImpl.java`（heredoc 覆盖） | **高**：覆盖全部手工改动 |
| `fix_yht_dict.sh` | 在实现类末尾拼入简化版 `convertCertType`/`convertAcctType` 并改 `signReq.setIdTp` | 中高：与其它改证件脚本可能叠加冲突 |
| `fix_cert_type.sh` / `fix_cert_mapping.sh` | 用 `awk` 替换 `convertCertType` 方法体为细分映射逻辑 | **高**：与 `fix_yht_dict`/`fix_cert_*` 另一版本互斥取决于当前文件形态 |
| `modify_ywglservice.sh` | `YwglServiceImpl` 注入 `YhtYwService` 并将 TODO 行改为真实调用 | 中 |

脚本之间 **没有内置统一编排器**；由下节推荐顺序或由人工根据 `git diff` 决定。

## 推荐执行顺序（仅供参考）

在 **明确需要从脚本基线重置实现类** 的前提下，可参照：

1. 模型与契约：`modify_entities.sh`、`modify_fkyw_skyw.sh`、`modify_dtos.sh`
2. 接口：`modify_yhtywservice.sh` → `fix_imports.sh`
3. 实现：若必须以脚本落盘整块逻辑，再执行 `fix_yht_impl.sh`（**会抹掉该类现有内容**）
4. 字典/映射：任选 **一套** `fix_yht_dict.sh` **或** `fix_cert_mapping.sh` / `fix_cert_type.sh`（执行前比对脚本与当前类中是否已有 `convertCertType`，避免重复或不一致）
5. 编排：`modify_ywglservice.sh`

若 **仅做小范围增量**，优先 **不使用** `fix_yht_impl.sh`，直接在 IDE 中编辑并编译验证。

## AI 与本工具链的衔接方式（核心要求）

- **先读脚本再执行**：执行任意根目录 `.sh` 前，必须用阅读工具浏览脚本正文，确认目标文件路径与操作类型（追加 / 替换 / 全量覆盖）。
- **禁止默认重复跑全套**：不得以「一劳永逸」为由循环执行全部脚本；每轮任务只运行与验收范围相关的脚本。
- **执行后验证**：实质性改动后须在 `prod` 工程侧执行模块编译或项目组规定的验证命令（由任务上下文与用户指定模块决定）；不得在无编译/检查输出的情况下宣称通过。
- **与统一主规则关系**：脚本不改变 `CLAUDE.md` 中的 Edict、交付门禁与优先级；项目层强制摘要见仓库根 `.cursorrules`；脚本只是 **可选加速器**，不能替代可读 diff 与验证。

## 相关路径

- 本项目入口（Cursor）：`.cursorrules`（见「工作区 prod 批处理脚本」）
- Trae 薄入口：`.trae/rules/workspace-common.md`（§3 约定与技能主源指针）
- 统一主规则（跨工作流）：`CLAUDE.md`
- 跨工具**通用**接入说明（不含项目脚本列举）：`doc/项目规范/统一主规则-通用工具链接入说明.md`
- 工具链技能：`doc/技能库/vfox-toolchain/SKILL.md`

## 与其它目录下脚本的区别

- `doc/技能库/brainstorming/scripts/*.sh`、`doc/技能库/systematic-debugging/find-polluter.sh` 服务于对应技能演练，路径与用途与本技能 **不同**；不要与根目录 `prod` 脚本混为一谈。
