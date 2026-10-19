# Git 提交描述模板

## 1. 适用范围
- 适用于当前 `Gjj` 工作区内所有代码、文档、脚本与规范类提交。
- 以 `doc/项目规范/第一研发中心-代码Commit提交规范说明.md` 为公司规范主源，本模板用于日常提交时快速套用。

## 1.1 提交仓库定位要求（AI 工具必读）

**提交必须在当前会话涉及的项目或子项目所属 Git 仓库中执行。先用本节目录索引定向定位，再核验 Git 根目录；不得默认在 `D:/Probject/Gjj` 根仓库提交业务代码，也不得为寻找提交位置反复全局扫描工作区。**

定位顺序：

1. **优先使用会话证据**：从用户指定的项目/地区、当前会话实际读取或修改的文件绝对路径、已确认的仓库与分支确定候选仓库。文件路径与本节索引按最长目录前缀匹配，命中嵌套独立仓库时选嵌套仓库。
2. **再查目录索引**：按“地区或工程目录 + 前后端 + 子项目”定位下表中的本地工作副本。`prod` 是目录标识，不等于生产分支；地区目录不等于同名分支。相同仓库名、相同 `origin` 的不同本地副本不能互相替代。
3. **只对候选目录核验**：执行 `git -C "<候选仓库绝对路径>" rev-parse --show-toplevel`，其结果必须等于预期仓库根目录；再检查当前分支、`origin`、状态与差异。若意外返回 Gjj 根目录，说明候选目录并非独立仓库，必须重新确认归属。
4. **按文件分组提交**：同一会话跨多个仓库时，按各文件最近的 Git 根目录分组，在每个实际涉及的仓库分别检查、同步、暂存、提交；分别记录仓库路径、分支、文件清单与提交结果。只处理本次会话授权范围，不能顺带提交其他地区副本或历史暂存内容。
5. **局部兜底**：索引路径不存在或 Git 根目录不匹配时，仅检查会话目标文件的父目录链及已确定地区的 `IdeaProject` / `WebStromProject`。只有发现新增、迁移仓库且局部定位失败时，才扩大到相关一级目录；仍无法确定时向用户确认项目/地区，不猜测仓库，不默认扫描整个 `D:/Probject/Gjj`。

### 1.2 Gjj 仓库目录索引

核验日期：2026-10-19。下表覆盖本机工作区已核实的 **23 个有效 Git 仓库**（Gjj 根仓库 1 个、业务工作副本 20 个、第三方源码仓库 2 个）。不包含依赖、构建产物、缓存及归档目录。

- 下表所有本地路径均相对 `D:/Probject/Gjj/`；执行命令时拼接为绝对路径。
- 业务仓库远端统一位于 `gitlab2.capinfo.com.cn/capinfo-gjj/`，表内“远端标识”省略该前缀及 `.git` 后缀，供核对 `origin`；根仓库和第三方仓库标明独立远端。
- `IdeaProject` 为后端目录；前端目录实际拼写为 **`WebStromProject`**，不得改写为 `WebStormProject`。
- 仓库内的 Maven 子模块、`src/`、`sql/`、`工程数据库设计/` 等使用所属仓库根目录提交，不以模块名推测新的独立仓库。

| 项目/地区 | 子项目/会话关键词 | 本地仓库根目录（相对 Gjj） | 远端标识 |
|---|---|---|---|
| Gjj 公共工作区 | doc、提交模板、规范、公共工具、一户通挡板 | `.` | `github.com/xueyuanxueyuan/MyProject` |
| prod | 认证后端 / auth | `prod/IdeaProject/capinfo-gjj-auth` | `platform/capinfo-gjj-auth` |
| prod | aiyw 后端 | `prod/IdeaProject/capinfo-gjj-busi-aiyw` | `busi/capinfo-gjj-busi-aiyw` |
| prod | gjtq 后端 | `prod/IdeaProject/capinfo-gjj-busi-gjtq` | `busi/capinfo-gjj-busi-gjtq` |
| prod | gzjc 后端 | `prod/IdeaProject/capinfo-gjj-busi-gzjc` | `busi/capinfo-gjj-busi-gzjc` |
| prod | 结算核算后端 / jshs | `prod/IdeaProject/capinfo-gjj-busi-jshs` | `busi/capinfo-gjj-busi-jshs` |
| prod | zhfw 后端 | `prod/IdeaProject/capinfo-gjj-busi-zhfw` | `busi/capinfo-gjj-busi-zhfw` |
| prod | 资金风控后端 / zjfk / 大模型后端 | `prod/IdeaProject/capinfo-gjj-busi-zjfk` | `busi/capinfo-gjj-busi-zjfk` |
| prod | 平台公共模块 / platform-public | `prod/IdeaProject/capinfo-gjj-platform-public` | `platform/capinfo-gjj-platform-public` |
| prod | 认证前端 / frontend-auth | `prod/WebStromProject/capinfo-gjj-frontend-auth` | `frontend/capinfo-gjj-frontend-auth` |
| prod | 结算核算国密前端 / jshs-gm | `prod/WebStromProject/capinfo-gjj-frontend-jshs-gm` | `frontend/capinfo-gjj-frontend-jshs-gm` |
| prod | 资金风控国密前端 / zjfk-gm / 大模型前端 | `prod/WebStromProject/capinfo-gjj-frontend-zjfk-gm` | `frontend/capinfo-gjj-frontend-zjfk-gm` |
| 嘉兴 / jiaxing | 结算核算后端 / 一户通 yht / SQL | `jiaxing/IdeaProject/capinfo-gjj-busi-jshs` | `busi/capinfo-gjj-busi-jshs` |
| 嘉兴 / jiaxing | 结算核算前端 / 非 gm 目录 | `jiaxing/WebStromProject/capinfo-gjj-frontend-jshs` | `frontend/capinfo-gjj-frontend-jshs` |
| 嘉兴 / jiaxing | 结算核算国密前端 / jshs-gm | `jiaxing/WebStromProject/capinfo-gjj-frontend-jshs-gm` | `frontend/capinfo-gjj-frontend-jshs-gm` |
| 临沂 / linyi | 结算核算后端 / jshs | `linyi/IdeaProject/capinfo-gjj-busi-jshs` | `busi/capinfo-gjj-busi-jshs` |
| 临沂 / linyi | 结算核算国密前端 / jshs-gm | `linyi/WebStromProject/capinfo-gjj-frontend-jshs-gm` | `frontend/capinfo-gjj-frontend-jshs-gm` |
| 温州 / wenzhou | 结算核算后端 / jshs | `wenzhou/IdeaProject/capinfo-gjj-busi-jshs` | `busi/capinfo-gjj-busi-jshs` |
| 温州 / wenzhou | 结算核算国密前端 / jshs-gm | `wenzhou/WebStromProject/capinfo-gjj-frontend-jshs-gm` | `frontend/capinfo-gjj-frontend-jshs-gm` |
| 枣庄 / zaozhuang | 结算核算后端 / jshs | `zaozhuang/IdeaProject/capinfo-gjj-busi-jshs` | `busi/capinfo-gjj-busi-jshs` |
| 枣庄 / zaozhuang | 结算核算国密前端 / jshs-gm | `zaozhuang/WebStromProject/capinfo-gjj-frontend-jshs-gm` | `frontend/capinfo-gjj-frontend-jshs-gm` |
| 第三方源码 | Mano 技能源码 | `codeworkfiles/mano-skill-source` | `github.com/Mininglamp-AI/mano-skill` |
| 第三方源码 | MoneyPrinterTurbo | `external/MoneyPrinterTurbo` | `github.com/harry0703/MoneyPrinterTurbo` |

第三方仓库仅在当前会话明确涉及其源码修改时作为提交候选，不能因位于工作区内就纳入 Gjj 业务提交。

### 1.3 非独立仓库目录与典型定位示例

| 会话涉及路径或场景 | 应选仓库/处理要求 |
|---|---|
| `doc/通用模板/Git提交描述模板.md`、`doc/项目规范/` | `D:/Probject/Gjj` 根仓库 |
| `yht-mock-server/` 一户通挡板 | `D:/Probject/Gjj` 根仓库；该目录没有独立 `.git` |
| `zjb-openapi-auto-test/` 自动化测试工具 | `D:/Probject/Gjj` 根仓库；当前已有跟踪文件，逐文件核对，不能沿用“未纳入版本管理”的旧结论 |
| `scripts/`、`outputs/`、`tmp/`、`config/` 等公共目录 | 无独立仓库时归属根仓库，但归属不等于允许入库；先检查目标文件是否已跟踪、是否被忽略和是否属于本次授权范围，禁止自动 `git add -f` |
| 嘉兴后端 `sql/初始/一户通表的建表语句.sql` 或 yht Java 代码 | `D:/Probject/Gjj/jiaxing/IdeaProject/capinfo-gjj-busi-jshs`，不提交到根仓库或 prod 同名副本 |
| `prod/IdeaProject/capinfo-gjj-busi-zjfk/` 内的大模型代码、测试、数据库脚本 | `D:/Probject/Gjj/prod/IdeaProject/capinfo-gjj-busi-zjfk` |
| 大模型后端与 `prod/WebStromProject/capinfo-gjj-frontend-zjfk-gm/` 同时修改 | 分别在 zjfk 后端仓库、zjfk-gm 前端仓库提交；公共 `doc/` 如获授权则另在根仓库提交 |

**只知道部署地区或业务名时不能直接推测代码目录**：例如枣庄环境运行的大模型功能，若当前会话修改文件位于 `prod/` 的 zjfk 仓库，仍应提交该实际仓库，不能改投 `zaozhuang/` 的 jshs 仓库。

### 1.4 定向核验与提交边界

以下为嘉兴后端示例，只读核验后再按既有提交流程处理：

```bash
git -C "D:/Probject/Gjj/jiaxing/IdeaProject/capinfo-gjj-busi-jshs" rev-parse --show-toplevel
git -C "D:/Probject/Gjj/jiaxing/IdeaProject/capinfo-gjj-busi-jshs" branch --show-current
git -C "D:/Probject/Gjj/jiaxing/IdeaProject/capinfo-gjj-busi-jshs" remote get-url origin
git -C "D:/Probject/Gjj/jiaxing/IdeaProject/capinfo-gjj-busi-jshs" status --short
git -C "D:/Probject/Gjj/jiaxing/IdeaProject/capinfo-gjj-busi-jshs" diff --name-status
git -C "D:/Probject/Gjj/jiaxing/IdeaProject/capinfo-gjj-busi-jshs" diff --cached --name-status
```

- 检查目标文件时使用 `git -C "<仓库绝对路径>" ls-files --error-unmatch -- "<仓库内相对文件路径>"`；未跟踪时再用 `check-ignore -v -- "<仓库内相对文件路径>"` 核对忽略原因，注意其退出码不能单独作为允许入库的依据。
- 暂存只用已核实的本次文件路径；禁止无范围的 `git add .`、`git add -A`。若暂存区已有其他会话内容，不得夹带提交，也不得擅自清空或撤销他人暂存；先确认提交边界，必要时请用户确认处理方式。
- 目录索引只负责定位仓库，不授予提交或推送权限；实际执行仍遵守用户授权、目标仓库自身规则和本文第 6 节的同步要求。
- 对用户回报提交时，至少列出“项目/地区、仓库绝对路径、分支、提交文件、commit id、推送状态”；不能仅报仓库名称，避免多个同名工作副本混淆。
- 新增或迁移仓库时，同步维护本节的路径、远端标识及核验日期；日常提交只核验当前命中的仓库，不重新枚举全工作区。

## 2. 标准格式
- Commit message 采用如下结构：

```text
<提交类型>: <简短说明>

<可选：补充说明>

AI辅助: true/false
AI工具: Trae/智谱/DeepSeek/ChatGPT/Claude/CodeX/Cursor/Qcoder/Other/None
```

- `<提交类型>` 建议使用 `feat`、`fix`、`test`、`refactor`、`docs`、`style`、`chore`、`build`、`ci`、`perf`、`other`。
- `<可选：补充说明>` 不强制填写；若填写，需说明本次改动的关键信息、影响范围或原因，内容保持具体可审计。

## 3. 字段要求
- `AI辅助` 只允许填写小写 `true` 或 `false`，禁止使用 `True/False`、`yes/no`、`是/否` 等其他写法。
- `AI工具` 必须填写公司规范允许值；当 `AI辅助: false` 时，`AI工具` 必须填写 `None`。
- 当 `AI辅助: true` 时，必须填写当前实际使用的主要 AI 工具；当前 Codex（含 Codex Desktop/CLI）统一填写 `CodeX`，不得因省事或枚举判断不清而填写 `Other`。
- `Other` 不是未知工具的默认值。无法确认当前工具名称，或实际工具不在枚举中时，提交前必须向用户求证，按用户确认结果填写并同步维护规范枚举。
- 当一次提交同时使用多个 AI 工具时，只填写对本次提交帮助最大的主要工具。
- 不再强制标题追加 `（AI生成提交）`，也不再强制补充说明固定写成“1. 改了什么 / 2. 为了解决什么问题”。

## 4. 提交模板
```text
feat: 你的提交标题

补充说明（可选，没有可删除本行及空行）

AI辅助: false
AI工具: None
```

## 5. 示例
### 5.1 AI 辅助提交
```text
fix: 修复结算核算分页查询条件丢失问题

修正翻页后查询条件未正确回填的问题，避免结果集与筛选条件不一致。

AI辅助: true
AI工具: Trae
```

### 5.2 非 AI 辅助提交
```text
chore: 调整结算日志输出格式

AI辅助: false
AI工具: None
```

## 6. 注意事项
- 提交标题与补充说明必须具体，禁止使用“修复问题”“优化代码”“调整一下”等空泛表述。
- 提交前仍需遵守“先同步远端主线、解决冲突、再本地提交”的既有流程要求。
- 默认仅提交本次修改涉及的源码文件；非源码文件是否提交，以当前项目规范和用户指令为准。
- 若公司后续调整 `AI工具` 枚举或解析口径，以公司规范主源和统计工具最新要求为准；任何情况下不得在工具身份未确认时自行使用 `Other` 兜底。
