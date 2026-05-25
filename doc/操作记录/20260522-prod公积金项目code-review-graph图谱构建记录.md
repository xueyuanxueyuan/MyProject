# prod 公积金项目 code-review-graph 图谱构建记录

## 1. 任务背景

用户要求以 `/home/source/Jetbrains/Probject/Gjj/prod` 为主分析并构建公积金项目图谱，避免仅分析根目录少量跟踪文件。

## 2. 前置核查

核查结果：

- `prod` 位于根仓库 `/home/source/Jetbrains/Probject/Gjj` 下。
- `prod` 自身不是 Git 根目录，初次被 `code-review-graph` 判定为缺少 `.git` 或 `.code-review-graph` 标识。
- `prod` 下粗略统计存在约 33735 个 Java/XML/SQL/前端等代码与配置类文件。
- `code-review-graph build/status/detect-changes` 均支持 `--repo` 参数。

## 3. 构建方式

为避免覆盖根目录图谱，本次使用独立数据目录：

```bash
/home/source/Jetbrains/Probject/Gjj/.code-review-graph-prod
```

由于 `prod` 缺少项目根标识，先创建：

```bash
mkdir -p /home/source/Jetbrains/Probject/Gjj/prod/.code-review-graph
```

随后执行：

```bash
UV_HTTP_TIMEOUT=300 uvx code-review-graph build \
  --repo /home/source/Jetbrains/Probject/Gjj/prod \
  --data-dir /home/source/Jetbrains/Probject/Gjj/.code-review-graph-prod \
  --skip-flows
```

说明：命令中使用 `--skip-flows` 减少大型项目初次构建的后处理压力。工具仍完成了最小后处理并生成 flow/community 统计。

## 4. 构建结果

构建输出摘要：

- 解析文件数：6755
- 构建阶段节点数：47862
- 构建阶段边数：272659
- FTS 索引节点数：45483
- Flow 数：6289
- Community 数：32
- Spring DI 解析：在 6143 个 Java 文件中解析到 15203 条 CALLS 边

状态检查命令：

```bash
UV_HTTP_TIMEOUT=300 uvx code-review-graph status \
  --repo /home/source/Jetbrains/Probject/Gjj/prod \
  --data-dir /home/source/Jetbrains/Probject/Gjj/.code-review-graph-prod
```

状态结果：

- Nodes: 45483
- Edges: 266388
- Files: 6755
- Languages: javascript, vue, typescript, java, sql
- Last updated: 2026-05-22T15:19:19

图谱数据库文件：

- `/home/source/Jetbrains/Probject/Gjj/.code-review-graph-prod/graph.db`
- 大小约 776876032 bytes

## 5. 覆盖范围分析

源码文件粗略统计：

- `.java`: 6143
- `.xml`: 531
- `.vue`: 397
- `.js`: 181
- `.yml`: 158
- `.md`: 146
- `.sql`: 46
- `.yaml`: 23
- `.ts`: 1

主要项目分布：

- `IdeaProjects/capinfo-gjj-busi-gjtq`: 3693
- `IdeaProjects/capinfo-gjj-busi-jshs`: 3197
- `WebstormProjects/capinfo-gjj-frontend-jshs-gm`: 587
- `IdeaProjects/capinfo-gjj-busi-jshs-v3`: 148
- `IdeaProjects/capinfo-gjj-platform-public`: 1

Maven 顶层项目：

- `prod/IdeaProjects/capinfo-gjj-busi-jshs-v3/pom.xml`
- `prod/IdeaProjects/capinfo-gjj-busi-gjtq/pom.xml`
- `prod/IdeaProjects/capinfo-gjj-busi-jshs/pom.xml`

## 6. 图谱结构分析

节点类型：

- Function: 31971
- File: 6755
- Class: 6652
- Test: 105

节点语言：

- java: 37641
- vue: 6270
- javascript: 1080
- sql: 490
- typescript: 2

边类型：

- CALLS: 157646
- IMPORTS_FROM: 57487
- CONTAINS: 41104
- INHERITS: 3655
- INJECTS: 3390
- TESTED_BY: 1542
- REFERENCES: 1511
- CONSUMES: 52
- PRODUCES: 1

节点数量较高的重点文件包括：

- `capinfo-gjj-busi-cwhs-jzgl/.../CwhsServiceImpl.java`
- `capinfo-gjj-busi-zjjs-ywgl/.../YwglServiceImpl.java`
- `capinfo-gjj-busi-cwhs-jzgl/.../CwhsController.java`
- `capinfo-gjj-busi-cwhs-jzgl/.../CwhsService.java`
- `capinfo-gjj-busi-zjjs-ywgl/.../YwglController.java`
- `capinfo-gjj-busi-gjtq-jzgl/.../JzglServiceImpl.java`
- `capinfo-gjj-busi-gjtq-hbjjk/.../HbjjkServiceImpl.java`

## 7. 变更影响分析

执行：

```bash
UV_HTTP_TIMEOUT=300 uvx code-review-graph detect-changes \
  --repo /home/source/Jetbrains/Probject/Gjj/prod \
  --brief
```

结果：

- 分析到 34 个变更文件。
- 识别到 0 个变更函数或类。
- 影响 Flow 数为 0。
- 测试缺口数为 0。
- 整体风险分为 0.00。

说明：当前根仓库存在较多变更，但这些变更主要不落在 `prod` 图谱可识别的函数/类范围内，因此以 `prod` 为 repo 的变更影响分析风险为 0。

## 8. 后续建议

1. 后续针对 `prod` 内具体模块做审查时，应继续显式带上：

```bash
--repo /home/source/Jetbrains/Probject/Gjj/prod --data-dir /home/source/Jetbrains/Probject/Gjj/.code-review-graph-prod
```

2. 若要提升社区检测精度，可后续考虑安装 `igraph` 相关可选依赖后重新构建。
3. 若只审查结算核算模块，可基于当前图谱重点关注 `capinfo-gjj-busi-jshs` 下 `cwhs`、`zjjs-ywgl`、`zjjs-lcgl` 等高节点密度文件。
4. 若只审查归集提取模块，可重点关注 `capinfo-gjj-busi-gjtq` 下 `jzgl`、`hbjjk` 等实现类。

## 9. 结论

已成功以 `/home/source/Jetbrains/Probject/Gjj/prod` 为主构建公积金项目图谱，根目录先前只解析到 1 个文件的问题已规避。当前可用于后续按模块做代码审查、影响范围分析与架构理解。
