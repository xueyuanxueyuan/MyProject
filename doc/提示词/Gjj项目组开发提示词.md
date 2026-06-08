# Gjj 项目组开发提示词

## 1. 定位
- 本文件保留为 **后端快捷提示词入口**。
- 详细通用约束优先参考：
  - `doc/项目规范/Gjj项目组开发规范.md`
  - `doc/提示词/Gjj项目组前后端项目开发提示词.md`

## 2. 使用规则
- 本文件只保留高频后端场景模板，不重复展开项目总规范。
- 默认仍需遵循：`Edict` 工作流、`anti-laziness-anti-hallucination`、`verification-before-completion`。

## 3. 数据库系统字段标准
- 涉及达梦建表脚本、物理模型设计、数据库设计文档时，统一使用以下 15 个系统字段，不得擅自改名或删减：
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
- 输出 SQL 时，表名和字段名统一使用大写，且不带引号。

## 4. 新增后端接口
```markdown
请基于 Gjj 当前后端工程结构实现以下接口需求：
【填写具体需求】

要求：
1. 先明确目标模块、目标层级、目标目录。
2. 中大型需求先产出 SDD 文档，再进入编码。
3. 保持分层清晰，返回统一 `R<T>`，入参与出参使用 DTO。
4. Controller 补齐校验、日志与接口注解。
5. 简单 CRUD 优先 MyBatis-Plus，复杂查询放 XML。
6. Feign 必须提供降级，且降级不得 `return null`。
7. 输出：修改文件清单、实现说明、风险点、验证步骤、文档路径。
```

## 5. 缺陷修复
```markdown
请排查并修复以下 Gjj 后端问题：
【贴现象/日志/报错】

输出：
1. 根因定位（controller/service/sql/feign/job）
2. 修复方案（最小改动）
3. 风险评估（兼容性/性能/一致性）
4. 回归用例（正常/异常/边界）
```

## 6. SQL 优化
```markdown
请优化以下 SQL 或 Mapper 方法：
【填 SQL 或 mapper 方法】

要求：
1. 业务语义与返回结构不变
2. 说明优化点与原因
3. 如有必要给出索引建议
4. 提供验证方式
```

## 7. Feign 降级治理
```markdown
请治理当前模块的 Feign 降级代码，重点处理 `return null`：
【填写模块或文件】

要求：
1. 不改接口签名
2. 中大范围治理先补 SDD
3. 降级必须可观测：日志 + `R.fail(...)` / `BizException`
4. 输出修复清单、风险清单、验证方式
```

## 8. 代码评审
```markdown
请对以下后端改动做规范视角代码评审：
【填写 MR / Diff / 文件列表】

重点关注：
1. SDD 是否到位
2. 分层是否清晰
3. 是否存在空降级、吞异常、无日志失败分支
4. 状态流转是否完整可追踪
5. SQL 是否存在全表扫描风险

请按：`致命 / 高 / 中 / 建议` 输出。
```

## 9. 提交前自检
```markdown
请按 Gjj 后端规范对当前改动执行提交前自检，并逐项输出“通过 / 不通过 / 需补充说明”：
- SDD 是否已确认
- 编译是否通过
- `R<T>` / DTO / 校验 / 日志 / 注解是否齐全
- Feign 降级是否无 `return null`
- SQL 是否具备合理筛选与分页
- 是否包含最小回归验证步骤
```
