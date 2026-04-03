# Gjj 项目组后端开发规范

## 1. 适用范围
- 适用于 `Gjj` 工作区内各城市/环境项目（如 `prod`、`linyi`、`zaozhuang`、`wenzhou`、`jiaxing`）。
- 适用于 Java 后端微服务，尤其是 Spring Boot + MyBatis-Plus + OpenFeign + Kafka + XXL-Job 体系。

## 1.1 文档落地目录约定（重要）
- **目标**：保证文档“按类型归档”，避免把项目组公共文档写进某个具体项目子目录导致找不到/不可复用。
- **目录映射（按你当前 `doc/` 一级目录约定）**：
  - **`doc/rules/`**：项目组开发规范、编码约束、流程/门禁规则（RULES）
    - 命名建议：`Gjj项目组xxx规范.md` / `xxx约定.md`
  - **`doc/prompt/`**：项目组通用提示词（PROMPTS），用于指导研发/排障/评审/生成变更说明
    - 命名建议：`Gjj项目组xxx提示词.md`
  - **`doc/design/`**：设计文档（架构/模块设计/方案评审稿/时序图/数据流等）
    - 命名建议：`YYYYMMDD-主题-设计.md` 或 `主题-设计.md`
  - **`doc/demand/`**：需求文档（业务需求、用户故事、验收标准、变更说明等）
    - 命名建议：`YYYYMMDD-主题-需求.md` 或 `主题-需求.md`
  - **`doc/analysis/`**：需求分析文档（需求拆解、规则提炼、验收口径、状态机/字段清单等）
    - 命名建议：`YYYYMMDD-主题-需求分析.md` 或 `主题-需求分析.md`
  - **`doc/review/`**：评审与复盘（评审记录、风险清单、上线复盘、问题根因分析）
    - 命名建议：`YYYYMMDD-主题-评审.md` / `YYYYMMDD-主题-复盘.md`
  - **`doc/proto/`**：接口/协议相关（报文样例、字段字典、协议约定、示例 payload）
    - 命名建议：`系统A-系统B-协议.md` / `topic-消息体.md`
  - **`doc/skills/`**：技能编写与归档的**首选分区**（源码/草稿/团队可读的完整说明均应先入此目录）。
    - **编写技能时的流程（必须）**：
      1. 在 `doc/skills/<技能目录名>/` 下创建或更新内容（建议包含 `SKILL.md` 及可选的 `reference.md` 等；结构可参考 Cursor 技能规范）。
      2. 再将同一套技能**同步**到仓库根目录所使用的智能体对应的skills目录下，例如使用Cursor智能体，同步到**`.cursor/skills/<技能目录名>/`**（Cursor 固定发现路径，须含 `SKILL.md`），保证 Agent 可加载。
    - **原则**：`doc/skills/` 为项目组内统一归档与评审入口；`.cursor/skills/` 为与 Cursor 集成的一致性副本，二者内容应保持**一致**（同步后若只改一处须说明原因或尽快补齐）。
  - **`doc/sprc/`**：你们现有分类（建议在本目录内新增 `README.md` 说明 sprc 的含义与放置边界，避免成员误用）
- **强约束**：
  - 项目组公共文档（规则/提示词）必须放在 `doc/rules/`、`doc/prompt/`。
  - 所有分析类工作必须产出对应分析文档并落盘到 `doc/analysis/`；不得仅在会话中给出口头分析结论。
  - 所有评审类工作必须产出对应评审文档并落盘到 `doc/review/`；评审文档需明确“认可项、问题项、处理结论/建议”。
  - 评审文档中的“差异项/分歧项/变更项”必须使用表格记录，至少包含：差异点、原口径、评审口径、处理结论、状态。
  - 新增或修订 Cursor 技能时，须按 **1.1 节**中 **`doc/skills/` → `.cursor/skills/`** 的流程同步，不得仅在 `.cursor/skills/` 落单文件而缺少 `doc/skills/` 归档（紧急热修除外，须在后续 MR 补齐归档）。
  - 除“确有项目私有性”文档外，不要写入 `doc/prod/...`、`doc/linyi/...` 等项目/城市子目录；若必须写入，需在文档头部标注“适用范围/项目名/环境”。
  - 在做评审时，如有不同意见，一定要给出详细且清晰的论据，目的是要确保评审结果最终能够达成一致。而不是循环讨论。
  - 缺陷修改属于强约束场景：必须以“不引入其他问题”为前提完成修复，且提交前必须完成编译通过与关键回归验证，并在交付说明中明确验证范围与结果。

## 2. 通用分层要求
- 分层必须符合 **DDD** 思想，推荐采用四层：**接口层（Interface/API）-> 应用层（Application）-> 领域层（Domain）-> 基础设施层（Infrastructure）**。
- **接口层（Interface/API）**：
  - 对外暴露 REST/Feign/RPC 接口的适配层，只做参数接收、校验、鉴权上下文传递、异常/返回值适配、调用应用层。
  - 对应现有包常见落点：`controller`、对外 `api`（Feign 接口、DTO、降级定义）。
- **应用层（Application）**：
  - 负责用例编排与事务边界：跨聚合协调、跨系统调用顺序、领域服务调用、发布领域事件/集成事件（如有）。
  - **不承载业务规则细节**（业务规则应下沉到领域层），避免把 `service` 写成“巨石业务类”。
  - 对应现有包常见落点：`service`（建议区分 `app/service` 或以命名体现用例：`XxxAppService`/`XxxApplicationService`）。
- **领域层（Domain）**：
  - 承载核心业务模型与规则：实体/值对象/聚合根、领域服务、领域事件、领域能力接口（Repository 等）。
  - 领域对象禁止依赖框架与外部系统实现（如 Feign、DB、MQ 客户端），保持可测试与可演进。
  - 对应现有包常见落点：`domain`、`dm`（建议 `dm` 仅作为领域模型与规则的承载，不混入持久化/DTO）。
- **基础设施层（Infrastructure）**：
  - 技术实现与外部资源访问：持久化（Mapper/XML）、外部系统适配（Feign Client 实现/SDK 封装）、消息、缓存、配置等。
  - 对应现有包常见落点：`mapper`、`xml`、`repository` 实现、`infra` 适配器等。

### 2.1 Domain 目录分层细则（含 `entity`、`dao` 等）
- **目标**：统一你们当前项目里 `domain`（含历史拼写 `doamin`）目录下 `entity`、`dao`、`service`、`repository` 等混放问题，确保“职责清晰、依赖单向、可渐进迁移”。
- **统一命名**：
  - 新代码统一使用 `domain` 目录名；历史 `doamin` 目录可保留但应逐步迁移。
  - 迁移期要求：同一业务域内不得同时新增 `domain` 与 `doamin` 两套并行实现；若历史包未迁完，只允许在既有包内小步演进并在 MR 说明迁移计划。

- **推荐目录结构（单业务域示例）**：
  - `xxx/domain/<boundedcontext>/entity/`：领域实体、聚合根（`XxxEntity`、`XxxAggregate`）
  - `xxx/domain/<boundedcontext>/valueobject/`：值对象（`XxxVO`）
  - `xxx/domain/<boundedcontext>/service/`：领域服务（`XxxDomainService`）
  - `xxx/domain/<boundedcontext>/event/`：领域事件（`XxxDomainEvent`）
  - `xxx/domain/<boundedcontext>/repository/`：仓储接口（`XxxRepository`，仅接口）
  - `xxx/domain/<boundedcontext>/factory/`：工厂（复杂创建逻辑）
  - `xxx/domain/<boundedcontext>/specification/`：领域规格/组合规则（可选）
  - `xxx/domain/<boundedcontext>/exception/`：领域异常（可选）
  - `xxx/infrastructure/persistence/entity/`：数据库持久化对象（`XxxDO`/`XxxPO`）
  - `xxx/infrastructure/persistence/dao/`：DAO/Mapper 接口（`XxxDao` / `XxxMapper`）
  - `xxx/infrastructure/persistence/repository/`：仓储接口实现（`XxxRepositoryImpl`）
  - `xxx/infrastructure/persistence/mapper/` + `resources/mapper/*.xml`：MyBatis 映射与 SQL

- **`entity` 放置规则（重点）**：
  - `domain/.../entity` 只放**领域实体**与聚合根，体现业务行为与不变量（例如状态流转、校验规则、额度计算）。
  - 禁止把 ORM 注解、MyBatis 注解、Swagger 注解直接加在领域实体上。
  - 禁止把 `Entity` 当 DTO/DO 透传到接口层或持久化层。

- **`dao` 放置规则（重点）**：
  - `dao` 属于**基础设施层**，推荐放 `infrastructure/persistence/dao`（或现有 `mapper` 包）。
  - `dao` 负责数据读写与查询拼装，不承载业务规则、不做流程编排。
  - `domain` 层禁止直接依赖 `dao`；必须通过 `repository` 接口抽象访问持久化能力。

- **`repository` 规则（接口在 Domain，实现在 Infrastructure）**：
  - `domain/.../repository/XxxRepository`：定义领域需要的持久化能力语义（如 `save`, `findById`, `findActiveBy...`）。
  - `infrastructure/.../repository/XxxRepositoryImpl`：调用 `dao/mapper` 与数据转换器完成实现。
  - `Application/Domain Service` 只依赖仓储接口，不依赖 `dao/mapper`。

- **对象模型边界（必须遵守）**：
  - `DTO`（接口出入参）仅在接口层/应用层边界出现。
  - `Entity`（领域实体）仅在领域层和应用层内部流转，不直接下沉到 DAO。
  - `DO/PO`（持久化对象）仅在基础设施层使用。
  - 需要转换时必须显式使用 Assembler/Converter（可按业务域建立 `converter` 包）。

### 2.2 依赖方向、命名规范与禁止项（详细）
- **依赖方向（硬约束）**：
  - 仅允许：`Interface -> Application -> Domain`。
  - `Infrastructure` 以实现方式“依赖 Domain 接口”；`Domain` 不反向依赖 `Infrastructure`。
  - 禁止：`controller` 直调 `dao/mapper`、`domain entity` 直调 Feign/Redis/MQ/DB。

- **命名规范（建议统一）**：
  - 领域实体：`GdzhxxEntity`、`JzsqAggregate`（中文拼音缩写 + 大驼峰）
  - 领域服务：`JzsqDomainService`
  - 应用服务：`JzsqAppService` / `JzsqApplicationService`
  - 仓储接口：`JzsqRepository`
  - 仓储实现：`JzsqRepositoryImpl`
  - 持久化对象：`GdzhxxDO`（或历史兼容 `GdzhxxPO`，同模块保持一致）
  - DAO 接口：`GdzhxxDao` 或 `GdzhxxMapper`（同模块保持一致，不混用）

- **禁止项（评审必查）**：
  - 禁止在 `domain` 包下出现 `dao`、`mapper`、`xml`、`feign` 实现类。
  - 禁止在 `domain/entity` 中出现数据库主键自增策略、分页参数、SQL 片段等持久化细节。
  - 禁止 `service.impl` 直接拼接 SQL 或直接操作 XML 命名空间字符串。
  - 禁止一个类同时扮演 DTO + Entity + DO 三种角色。

- **历史项目兼容策略（增量治理）**：
  - 允许旧项目短期保留 `service + dao + entity` 传统结构，但新需求改动必须按 DDD 方向收敛：
    1. 先补 `domain/repository` 接口；
    2. 再把 `dao` 调用收口到 `repository impl`；
    3. 最后将关键业务规则迁入 `domain/entity` 或 `domain/service`。
  - 对超大历史类（如 `*ServiceImpl`）采用“切片迁移”：每次需求至少抽离一个完整用例到应用层 + 领域层，不做一次性大爆炸重构。

### 2.3 命名规范（中文拼音缩写 + 驼峰，必须）
- **总原则**：
  - 项目中的变量、对象、接口、方法、类、枚举、包名等，统一遵循**中文语义的拼音缩写命名**。
  - 命名风格统一使用驼峰：类/接口使用**大驼峰**，变量/方法使用**小驼峰**。
  - 禁止使用无语义的单字母（循环计数器 `i/j/k` 等临时变量除外）与随意缩写（如 `tmp1`, `abc`, `testObj`）。

- **类、对象、接口命名**：
  - 类名/接口名：大驼峰 + 拼音缩写，如 `GjjZhxxController`、`JzsqAppService`、`GdzhxxRepository`。
  - 对象实例名：小驼峰 + 拼音缩写，如 `gjjZhxxService`、`gdzhxxRepository`。
  - DTO/DO/VO/Entity/Enum 后缀必须保留，前缀采用业务拼音缩写，如 `JzsqReqDTO`、`GdzhxxDO`、`DkztEnum`。

- **方法命名**：
  - 方法名使用“小驼峰 + 动词开头 + 业务拼音缩写”，如 `queryGdzhxxByGrzh()`、`saveJzsqxx()`、`updateDkztByYwlsh()`。
  - 布尔方法建议使用 `is/has/can` 前缀，如 `isSfzhValid()`、`hasYycs()`。
  - 禁止方法名仅写 `do`, `handle`, `process` 等泛化词而无业务语义。

- **变量与常量命名**：
  - 普通变量：小驼峰，如 `grzh`、`ywlsh`、`jzje`、`jkhtbh`。
  - 集合变量建议复数语义，如 `grzhList`、`jzsqxxSet`、`dkjlMap`。
  - 常量使用全大写下划线，如 `MAX_JZ_CS`、`DEFAULT_DK_LX`；常量语义优先中文拼音缩写映射。
  - 禁止同一作用域出现语义接近但难区分变量名（如 `jzxx`, `jzXx`, `jzxxx`）。

- **包名与分层命名**：
  - 包名保持小写，可用拼音缩写组合，如 `cn.capinfo.gjj.busi.jzgl.domain.jzsq.entity`。
  - 分层后缀保持约定：`controller`、`app/service`、`domain/service`、`repository`、`dao/mapper`、`converter`。
  - 同一模块内，拼音缩写口径必须统一（例如“缴存”统一 `jc`，不得同时出现 `jc`/`jiaocun`/`jiaoCun`）。

- **缩写词典与评审要求**：
  - 各业务域应维护最小缩写词典（建议放 `doc/rules` 或模块 `README`），如：`个人账号=grzh`、`业务流水号=ywlsh`、`借款合同编号=jkhtbh`。
  - 代码评审必须检查命名是否符合“拼音缩写 + 驼峰”；不符合项原则上需在合并前整改。
  - 新增缩写必须“先定义再使用”，避免同义多写（如 `grxx` 与 `gerenxx` 并存）。

## 3. 接口规范
- 统一返回 `R<T>`，禁止返回裸对象。
- 入参/出参必须 DTO 化（`ReqDTO` / `RespDTO`），避免直接暴露 DO/Entity。
- 入参校验使用项目校验注解（如 `@Check`）或等效校验机制。
- 核心接口补齐操作日志与 OpenAPI 注解（如 `@OptLog`、`@Operation`）。

## 4. 异常与日志规范
- 业务异常统一使用 `BizException`（带业务码和可读文案）。
- 严禁吞异常或空 catch。
- 日志最少包含：入口、关键分支、外部调用结果、异常堆栈。
- 日志中禁止打印敏感信息（身份证号全量、账号全量、token、密钥）。

## 5. 数据访问规范
- 简单 CRUD 优先 MyBatis-Plus（`LambdaQueryWrapper`）。
- 复杂联表、聚合、分页查询放 Mapper XML。
- XML 要求：
  - `namespace` 与 Mapper 接口一致；
  - 公共列定义可复用；
  - 动态 SQL 使用 `<where>/<if>/<foreach>`，避免字符串拼接。
- 逻辑删除字段与全局配置保持一致（常见为 `del_flag`）。

## 6. Feign 与降级规范
- `@FeignClient` 必须明确 `name/path`，按需配置 `url` 占位。
- 必须配置降级（`fallback` 或 `fallbackFactory`）。
- 降级实现禁止 `return null`，必须：
  - 返回 `R.fail(...)`，或
  - 抛出明确业务异常。

## 7. 事务与一致性
- 涉及多表更新、状态流转必须定义事务边界。
- 事件发布、外部调用与本地事务关系需明确（本地消息表/最终一致性策略）。
- 关键状态字段（如交易状态、审批状态）变更必须可追踪、可回放。

## 8. 定时任务与异步
- JobHandler 仅负责触发，具体逻辑下沉 Service。
- 异步任务需具备：超时策略、失败日志、幂等控制、重试边界。
- Topic、Job 名称统一集中常量管理，禁止硬编码散落。

## 9. 配置与安全
- 配置分环境维护（`application.yml` + profile 覆盖）。
- 外部地址、开关、阈值走配置项；禁止硬编码到代码。
- 密钥、token、证书文件、账号信息通过安全配置通道管理，不入库不入代码仓。

## 9.1 工具链与构建（vfox 必须项）
- **统一要求**：本项目组的 JDK、Node.js、Maven 等 SDK 由 **vfox** 管理；在执行任何编译/打包/发布命令前，必须先用 vfox 切换到本项目要求的 SDK 版本。
- **后端（Java）要求**：后端服务统一使用 **JDK 17**。
  - 切换命令（固定版本）：`vfox use java@17.0.2+8`
  - 校验命令：`java -version`（必须显示 17.x）
  - 校验命令：`mvn -v`（必须显示使用的 Java 为 17）
- **执行顺序（示例）**：
  - `vfox use java@17.0.2+8`
  - `java -version`
  - `mvn -v`
  - 再执行各模块的 `mvn clean package` / `mvn -pl ... package` 等构建命令
- **禁止项**：
  - 禁止依赖系统默认 `java`/`mvn` 版本“碰运气”构建。
  - 禁止在同一终端会话里切换 SDK 后不做版本校验直接打包。

## 10. 代码变更原则
- 最小可用改动：优先修复与需求直接相关代码。
- 避免无业务价值的大规模重命名和结构迁移。
- 兼容历史接口契约，新增能力尽量不破坏调用方。

## 11. 质量门禁
- 至少通过模块级编译与基础回归。
- 变更必须提供：
  - 影响范围（模块/接口/表）；
  - 验证清单（正常/异常/边界）；
  - 风险点及回滚思路。

## 12. 增量治理要求
- 新增代码不得引入 `return null` 的 Feign 降级实现。
- 新增接口必须补齐校验、日志、注解、返回包装。
- 对已触达的历史坏味道（空降级、超长类、无语义日志）做小步治理。

## 13. 智能体协同研发必遵规范（强制）
- 为保证“需求 -> 分析 -> 设计 -> 开发 -> 测试 -> 发布 -> 复盘”流程可持续推进，以下文档为项目组**必须遵循**的标准模板，适用于人工研发与 AI 协同研发场景：
  - 发布检查清单：`doc/rules/release-checklist.md`
  - 复盘报告模板：`doc/review/postmortem.md`
  - 会话进度模板：`doc/rules/claude-progress-template.txt`
- 强制要求：
  - 所有上线发布前，必须按 `doc/rules/release-checklist.md` 逐项勾检并留档。
  - 所有 P0/P1 事故、关键故障、回滚事件后，必须基于 `doc/review/postmortem.md` 完成复盘并落盘。
  - 所有 AI 协同会话或跨人接力开发任务，必须使用 `doc/rules/claude-progress-template.txt` 记录会话进展与交接信息。
  - 未按上述模板产出文档的任务，视为流程不完整，不得标记“完成”或“可发布”。
  - 评审与审计时，以上三类文档作为必查项。
