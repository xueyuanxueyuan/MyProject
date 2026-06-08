# Gjj 项目组前后端项目开发提示词

## 1. 定位
- 本文件是当前工作区的 **主提示词入口**。
- 适用于新增功能、缺陷修复、重构优化、代码评审、自检、联调和智能体协同研发。
- 详细规范主源优先参考：
  - `doc/项目规范/Gjj项目组开发规范.md`
  - `doc/项目规范/Gjj项目组智能体协同开发规范.md`

## 2. 核心约束速记
- 默认先走 `Trae Edict（三省六部）`，再并行启用防偷懒与交付前验证。
- 先判断任务阶段：需求 / 分析 / 设计 / 开发 / 评审 / 发布 / 复盘。
- 编码前先明确目标项目、目标模块、目标层级、目标目录。
- 中大型需求或重构先产出 SDD，并落盘到 `doc/设计文档/`。
- 分析落 `doc/需求分析/`，评审落 `doc/评审记录/`，操作记录落 `doc/操作记录/`。
- 交付前必须给出影响范围、验证步骤、验证结果、风险点和文档路径。

## 3. 项目结构速记
### 3.1 后端
- 聚合工程：`capinfo-gjj-busi-jshs`
- 常见模块：`ywgl`、`zhgl`、`lcgl`、`sp`、`cwhs`、`jhgl`、`zbkh`、`core`
- 典型分层：
  - `*-basic-svc-api` / `*-basic-svc-api-v2`：DTO、Feign、常量、接口契约
  - `*-basic-svc-app`：启动、配置、装配
  - `*-basic-svc-busi`：Controller、Service、Domain、DAO、Mapper、业务实现

### 3.2 前端
- 前端体系：`Vue 2 + Vue CLI + Element UI + Vuex + Vue Router + Axios`
- 典型分层：
  - `src/api`
  - `src/components/common`
  - `src/components/<业务域>`
  - `src/common`
  - `src/router`
  - `src/store`
  - `src/assets`

## 4. 数据库系统字段标准
- 涉及 Gjj 达梦建表、DDL 设计、物理模型输出时，默认使用以下 15 个系统字段，不得自行删减或替换命名。
- 标准字段顺序：
  - `ID BIGINT NOT NULL`
  - `ZXBH NVARCHAR(15) NOT NULL`
  - `REVISION INT NOT NULL`
  - `CREATOR NVARCHAR(100)`
  - `CREATED_TIME TIMESTAMP(0) NOT NULL DEFAULT CURRENT_TIMESTAMP()`
  - `UPDATOR NVARCHAR(100)`
  - `UPDATED_TIME TIMESTAMP(0)`
  - `JBJGBH NVARCHAR(30) NOT NULL`
  - `JBJGMC NVARCHAR(90) NOT NULL`
  - `WDBH NVARCHAR(30)`
  - `WDMC NVARCHAR(90)`
  - `QDLX NVARCHAR(10)`
  - `QDBM NVARCHAR(16)`
  - `DEL_FLAG NVARCHAR(1) NOT NULL DEFAULT '0'`
  - `HSJGBH NVARCHAR(10) NOT NULL DEFAULT '01'`
- 输出 DDL 时，表名与字段名统一使用大写，且不带引号。

## 5. 通用主提示词
```markdown
请作为 Gjj 当前工作区的资深研发工程师开展工作，严格基于现有项目结构与真实代码风格实现需求，不得套用脱离项目现状的通用模板。

请严格遵循以下要求：
1. 先判断任务属于前端、后端还是前后端联动。
2. 先判断涉及的业务模块、代码层级与目标目录。
3. 如任务属于分析、设计、评审，必须落盘到对应 `doc` 目录。
4. 如属于中大型需求或重构，必须先产出 SDD。
5. 后端必须遵循 DDD 边界、`R<T>`、DTO、BizException、Feign 降级规范。
6. 前端必须遵循现有分层、复用公共组件、统一请求封装与 `Vue 2 Options API` 风格。
7. 编译、打包、发布前必须校验工具链版本；后端 Java 默认 `vfox use java@17.0.2+8`。
8. 输出必须包含：目标项目与模块、代码放置位置、修改文件清单、实现思路、风险与影响范围、验证步骤与结果、对应文档路径。

待处理任务如下：
【在这里填写你的具体需求】
```

## 6. 后端快捷模板
```markdown
请基于 Gjj 当前后端工程结构完成以下后端需求：
【填写需求】

要求：
1. 先判断需求归属模块，如 `ywgl`、`zhgl`、`lcgl`、`sp`、`cwhs`、`jhgl`、`zbkh`。
2. 明确新增代码进入 `api/api-v2`、`app` 还是 `busi`。
3. 接口统一 `R<T>`，入参出参 DTO 化，业务异常使用 `BizException` 或等效异常。
4. Feign 必须配置降级，且降级不得 `return null`。
5. 若属于中大型需求或重构，编码前先补 SDD。
6. 输出：目标模块与目录、修改文件、实现说明、风险与回归范围、自检结果、文档路径。
```

## 7. 前端快捷模板
```markdown
请基于 `capinfo-gjj-frontend-jshs-gm` 现有结构完成以下前端需求：
【填写需求】

要求：
1. 明确目标业务域与目标目录。
2. 接口放 `src/api/<业务域>`，页面与业务组件放 `src/components/<业务域>`。
3. 真实复用能力才允许进入 `common` 或公共层。
4. 优先复用 `x-table`、`x-dialog`、`cap-form` 与统一请求封装。
5. 保持 `Vue 2 Options API` 风格，不引入割裂写法。
6. 输出：目标目录、修改文件、实现思路、风险与影响范围、验证步骤、文档路径。
```

## 8. 重构与自检模板
```markdown
请对以下改动执行重构评估或提交前自检：
【填写文件 / 目标 / Diff】

检查项：
- 代码是否放在正确目录
- 是否保持原业务逻辑、接口契约、数据口径、页面交互结果不变
- 是否已补齐分析 / 设计 / 评审文档
- 是否已完成关键正常流、异常流、边界流验证
- 是否已给出影响范围、风险点与回滚思路
```

## 9. 区域索引入口
- `prod` 主索引：`doc/提示词/Gjj项目-prod主索引与区域大索引提示词.md`
- 区域差异清单：`doc/提示词/Gjj项目-区域差异方法清单提示词.md`
- 白名单版差异清单：`doc/提示词/Gjj项目-区域差异方法清单-模块白名单版.md`
- 涉及区域差异任务时，默认先命中 `prod`，再看区域差异。
